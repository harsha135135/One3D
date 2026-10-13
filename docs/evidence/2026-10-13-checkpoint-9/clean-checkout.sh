#!/bin/bash
# Checkpoint 9: the Checkpoint 7 clean-checkout check, repeated at the release
# revision and cloned from GitHub as the README says. It also checks what
# Checkpoint 8 added: Prometheus scraping the backend, Grafana, and the
# management port staying unpublished.
#   clean-checkout.sh <repository url> <revision> <empty work directory>
# The only change from the README is COMPOSE_PROJECT_NAME, so this run gets
# its own empty volumes and leaves an existing local stack's data alone (stop
# that stack first: both use the same loopback ports). Needs git, docker,
# curl, jq and python3.
set -u
REPO=$1 REVISION=$2 WORK=$3
export COMPOSE_PROJECT_NAME=one3d-clean
F=http://localhost:8088
SIM=http://127.0.0.1:8090
PRINTER=0d3d0000-0000-4000-8000-000000000101
CFG=0d3d0000-0000-4000-8000-000000000002
C=$WORK/customer.jar O=$WORK/operator.jar
say() { echo; echo "## $*"; }
xsrf() { awk '$6 == "XSRF-TOKEN" { print $7 }' "$1"; }
login() {
  local auth callback
  auth=$(curl -s -c "$1" -b "$1" -o /dev/null -w '%{redirect_url}' $F/oauth2/authorization/oidc)
  callback=$(curl -s -o /dev/null -w '%{redirect_url}' -X POST "$auth" --data-urlencode "username=$2" --data-urlencode "claims=")
  curl -s -c "$1" -b "$1" -o /dev/null -w "sign-in as $2: HTTP %{http_code} (to the site after the mock provider's form)\n" "$callback"
}
api() { # api <jar> <method> <path> [json]
  curl -s -b $1 -c $1 -X $2 "$F$3" -H "X-XSRF-TOKEN: $(xsrf $1)" ${4:+-H 'Content-Type: application/json' -d "$4"}
}

say "1. A clean checkout of $REVISION from $REPO"
git clone -q "$REPO" "$WORK/One3D" && cd "$WORK/One3D" && git checkout -q "$REVISION" || exit 1
echo "commit: $(git rev-parse --short HEAD); untracked or changed files: $(git status --porcelain | wc -l | tr -d ' ');" \
  ".env present: $([ -e .env ] && echo yes || echo no)"

say "2. docker compose up -d --build (as documented)"
start=$(date +%s)
docker compose up -d --build --quiet-pull > "$WORK/up.log" 2>&1 || { tail -30 "$WORK/up.log"; exit 1; }
for i in $(seq 1 90); do
  [ "$(docker compose ps --format '{{.Health}}' backend frontend db simulator | sort -u | tr -d '\n')" = healthy ] && break
  sleep 2
done
echo "build and start: $(( $(date +%s) - start )) s (Docker's layer cache from earlier builds on this machine was available)"
docker compose ps --format '{{.Service}}: {{.Status}} {{.Ports}}'
echo "fresh volumes: $(docker volume ls --format '{{.Name}}' | grep "^$COMPOSE_PROJECT_NAME" | tr '\n' ' ')"

say "3. The site through the local Nginx on $F"
echo "GET /: HTTP $(curl -s -o /dev/null -w '%{http_code}' $F/)"
echo "GET /api/catalog/configurations: $(curl -s $F/api/catalog/configurations | jq -r '.[0] | "\(.productName // .name) (\(.price.amountMinor // .priceMinor) paise)"')"
echo "GET /api/orders without a session: HTTP $(curl -s -o /dev/null -w '%{http_code}' $F/api/orders)"

say "4. A customer orders and uses the demo payment shortcut; an operator produces and hands it over"
login $C clean-customer; login $O operator-clean
echo "payment mode: $(api $C GET /api/me | jq -r .paymentMode)"
ORDER=$(api $C POST /api/orders "{\"productConfigurationId\":\"$CFG\",\"quantity\":1}" | jq -r .id)
echo "order ${ORDER:0:8}: $(api $C GET /api/orders/$ORDER | jq -r .status)"
echo "demo shortcut: $(api $C POST /api/demo/orders/$ORDER/simulate-payment | jq -r '"\(.status), \(.confirmationSource)"')"
JOB=$(api $O POST /api/ops/orders/$ORDER/release | jq -r .id)
ATTEMPT=$(api $O POST /api/ops/jobs/$JOB/attempts "{\"printerId\":\"$PRINTER\"}" | jq -r '.attempts[-1].id')
for i in $(seq 1 15); do [ "$(api $O GET /api/ops/jobs/$JOB | jq -r '.attempts[-1].status')" = RUNNING ] && break; sleep 1; done
echo "attempt on the simulator: $(api $O GET /api/ops/jobs/$JOB | jq -r '.attempts[-1].status')"
curl -s -o /dev/null -X POST $SIM/dev/printers/$PRINTER/complete
for i in $(seq 1 15); do [ "$(api $O GET /api/ops/jobs/$JOB | jq -r .status)" = AWAITING_QC ] && break; sleep 1; done
echo "after the simulator completes: $(api $O GET /api/ops/jobs/$JOB | jq -r .status)"
echo "QC: $(api $O POST /api/ops/attempts/$ATTEMPT/qc '{"result":"PASSED","notes":"Whole-part check"}' | jq -r .status)," \
  "ready: $(api $O POST /api/ops/orders/$ORDER/ready-for-handoff | jq -r .status)," \
  "handed over: $(api $O POST /api/ops/orders/$ORDER/handed-over | jq -r .status)"

say "5. A model file through the local storage route (/s3 on the same origin)"
python3 -c "import os,struct,sys; sys.stdout.buffer.write(b'clean checkout'.ljust(80,b' ')+struct.pack('<I',40)+os.urandom(2000))" > "$WORK/part.stl"
UP=$(api $C POST /api/files/uploads "{\"filename\":\"part.stl\",\"sizeBytes\":$(wc -c < "$WORK/part.stl" | tr -d ' ')}")
FILE=$(echo "$UP" | jq -r .artifact.id)
echo "upload URL starts with: $(echo "$UP" | jq -r .upload.url | cut -c1-27)…"
echo "PUT: HTTP $(curl -s -o /dev/null -w '%{http_code}' -X PUT --data-binary @"$WORK/part.stl" -H 'Content-Type: application/octet-stream' "$(echo "$UP" | jq -r .upload.url)")"
api $C POST /api/files/$FILE/finalize > /dev/null
for i in $(seq 1 30); do [ "$(api $C GET /api/files/$FILE | jq -r .status)" = VERIFIED ] && break; sleep 1; done
echo "file ${FILE:0:8}: $(api $C GET /api/files/$FILE | jq -r .status)"

say "6. Monitoring: Prometheus reads the backend; Grafana answers; the management port is not published"
for i in $(seq 1 20); do
  health=$(curl -s http://127.0.0.1:9091/api/v1/targets | jq -r '.data.activeTargets[] | select(.labels.job == "one3d-backend") | .health')
  [ "$health" = up ] && break; sleep 3
done
echo "Prometheus target one3d-backend: ${health:-none}"
echo "Grafana login page on 127.0.0.1:3001: HTTP $(curl -s -o /dev/null -w '%{http_code}' http://127.0.0.1:3001/login)"
echo "management port 8081 from the host: $(curl -s -o /dev/null -w '%{http_code}' --max-time 3 http://127.0.0.1:8081/actuator/health || true) (000 = not published)"
echo "backend log format: $(docker compose logs --no-log-prefix backend 2>/dev/null | grep -c '^{"@timestamp"') JSON lines"

say "7. Replace every container; the records stay"
docker compose up -d --force-recreate 2>&1 | grep -c Recreated | sed 's/^/containers recreated: /'
for i in $(seq 1 90); do
  [ "$(docker compose ps --format '{{.Health}}' backend frontend db simulator | sort -u | tr -d '\n')" = healthy ] && break
  sleep 2
done
login $C clean-customer
echo "order ${ORDER:0:8}: $(api $C GET /api/orders/$ORDER | jq -r .status); file ${FILE:0:8}: $(api $C GET /api/files/$FILE | jq -r .status)"
api $C GET /api/orders/$ORDER/timeline | jq -r '.[] | "  \(.kind)"'

say "8. Remove this run (its containers and its volumes only)"
docker compose down -v 2>&1 | grep -cE "Removed" | sed 's/^/removed: /'
