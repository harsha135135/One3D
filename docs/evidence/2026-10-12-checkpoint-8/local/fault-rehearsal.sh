#!/bin/bash
# Checkpoint 8, local rehearsal of the fault exercise on the whole stack in
# containers (docker compose up -d --build; site on :8088, Prometheus on :9091).
# Fault: the printer simulator stops while an operator starts a print. The
# script then reads what an operator would look at (Prometheus and the JSON
# logs), brings the simulator back and reconciles the uncertain start.
#   fault-rehearsal.sh <screenshot dir>
# Needs curl, jq, docker and node with frontend/node_modules (for Grafana).
set -u
ROOT=$(cd "$(dirname "$0")/../../../.." && pwd)
HERE=$(cd "$(dirname "$0")" && pwd)
SHOTS=${1:-$HERE}
F=http://localhost:8088
PROM=http://localhost:9091
SIM=http://127.0.0.1:8090
PRINTER=0d3d0000-0000-4000-8000-000000000101
CFG=0d3d0000-0000-4000-8000-000000000002
WORK=$(mktemp -d); trap 'rm -rf "$WORK"' EXIT
C=$WORK/customer.jar O=$WORK/operator.jar
RUN=$(date +%s)
say() { echo; echo "## $*"; }
xsrf() { awk '$6 == "XSRF-TOKEN" { print $7 }' "$1"; }
login() {
  local auth callback
  auth=$(curl -s -c "$1" -b "$1" -o /dev/null -w '%{redirect_url}' $F/oauth2/authorization/oidc)
  callback=$(curl -s -o /dev/null -w '%{redirect_url}' -X POST "$auth" --data-urlencode "username=$2" --data-urlencode "claims=")
  curl -s -c "$1" -b "$1" -o /dev/null "$callback"
}
api() { curl -s -b $1 -c $1 -X $2 "$F$3" -H "X-XSRF-TOKEN: $(xsrf $1)" ${4:+-H 'Content-Type: application/json' -d "$4"}; }
# q <PromQL>: the value(s) Prometheus has now, one "labels value" line each.
q() {
  curl -s --get "$PROM/api/v1/query" --data-urlencode "query=$1" | jq -r '.data.result[] |
    "  \(.metric | del(.__name__, .job, .instance) | to_entries | map("\(.key)=\(.value)") | join(",")) \(.value[1])"'
}
attempt_status() { api $O GET /api/ops/jobs/$JOB | jq -r '.attempts[-1].status'; }
metrics() {
  echo "one3d_simulator_unreachable_total:"; q 'one3d_simulator_unreachable_total'
  echo "one3d_dispatch_results_total:"; q 'one3d_dispatch_results_total'
  echo "one3d_attempts_awaiting_reconciliation:"; q 'one3d_attempts_awaiting_reconciliation'
  echo "one3d_work_items{kind=DISPATCH_START} (non-zero):"; q 'one3d_work_items{kind="DISPATCH_START"} > 0'
  echo "one3d_work_oldest_age_seconds{kind=DISPATCH_START}:"; q 'one3d_work_oldest_age_seconds{kind="DISPATCH_START"}'
}
scrape() { sleep 20; }   # Prometheus reads the backend every 15 s

say "1. An order is confirmed (demo shortcut) and released"
login $C fault-customer-$RUN; login $O operator-fault-$RUN
ORDER=$(api $C POST /api/orders "{\"productConfigurationId\":\"$CFG\",\"quantity\":1}" | jq -r .id)
api $C POST /api/demo/orders/$ORDER/simulate-payment > /dev/null
JOB=$(api $O POST /api/ops/orders/$ORDER/release | jq -r .id)
CYCLE=$(api $O GET /api/ops/printers | jq -r ".[] | select(.id == \"$PRINTER\") | .bedCycleAttemptId // empty")
[ -n "$CYCLE" ] && api $O POST /api/ops/printers/$PRINTER/clear-bed "{\"attemptId\":\"$CYCLE\"}" > /dev/null
echo "order ${ORDER:0:8} confirmed, job ${JOB:0:8} released; printer bed clear"
scrape; metrics

say "2. Fault: the simulator stops; the operator starts the print"
docker compose -f "$ROOT/compose.yaml" -f "$ROOT/compose.override.yaml" stop simulator 2>&1 | tail -1
FAULT_AT=$(date -u +%T)
ATTEMPT=$(api $O POST /api/ops/jobs/$JOB/attempts "{\"printerId\":\"$PRINTER\"}" | jq -r '.attempts[-1].id')
echo "attempt ${ATTEMPT:0:8} recorded at $FAULT_AT UTC; now: $(attempt_status)"
sleep 45
echo "after 45 s: attempt $(attempt_status)"
scrape; metrics

say "3. The logs for that attempt (JSON lines carrying its attempt_id)"
docker compose -f "$ROOT/compose.yaml" -f "$ROOT/compose.override.yaml" logs --no-log-prefix --since 3m backend 2>/dev/null \
  | grep "\"attempt_id\":\"$ATTEMPT\"" | jq -c '{time: ."@timestamp"[11:19], level: .log.level, message, attempt_id, command_id, job_id}' | head -6
echo "start deliveries whose tries ran out (operator list; this local database also keeps earlier runs):" \
  "$(api $O GET '/api/ops/work?status=EXHAUSTED' | jq '[.[] | select(.kind == "DISPATCH_START")] | length')"
node "$HERE/grafana-screenshot.cjs" http://localhost:3001 "$SHOTS/grafana-during-fault.png" 2>&1 | sed 's/^/screenshot: /'

say "4. Recovery: the simulator is back; the operator reconciles the uncertain start"
docker compose -f "$ROOT/compose.yaml" -f "$ROOT/compose.override.yaml" start simulator 2>&1 | tail -1
for i in $(seq 1 30); do curl -sf -o /dev/null $SIM/status && break; sleep 1; done
echo "reconcile: $(api $O POST /api/ops/attempts/$ATTEMPT/reconcile | jq -r '.attempts[-1].status // .status // .code')"
for i in $(seq 1 20); do [ "$(attempt_status)" = RUNNING ] && break; sleep 1; done
echo "attempt: $(attempt_status)"
curl -s -o /dev/null -X POST $SIM/dev/printers/$PRINTER/complete
for i in $(seq 1 20); do [ "$(api $O GET /api/ops/jobs/$JOB | jq -r .status)" = AWAITING_QC ] && break; sleep 1; done
echo "job after the simulator completes: $(api $O GET /api/ops/jobs/$JOB | jq -r .status)"
scrape; metrics
node "$HERE/grafana-screenshot.cjs" http://localhost:3001 "$SHOTS/grafana-after-recovery.png" 2>&1 | sed 's/^/screenshot: /'
