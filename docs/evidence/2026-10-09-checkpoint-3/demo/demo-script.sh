#!/bin/bash
# Checkpoint 3 local demonstration: backend (demo profile, background worker on),
# PostgreSQL and the simulator from compose.yaml, seeded printer SIM-X1C-01.
# Run from the repository root; needs curl, jq and docker compose.
B=http://127.0.0.1:8080; S=http://127.0.0.1:8090
P=0d3d0000-0000-4000-8000-000000000101
J='Content-Type: application/json'
say() { echo; echo "## $*"; }
job_status() { curl -s $B/api/ops/jobs/$1 | jq -c "{status, attempts: [.attempts[] | {n: .attemptNumber, status}]}"; }
new_job() { local o; o=$(curl -s -X POST $B/api/orders -H "$J" -d '{"productConfigurationId":"0d3d0000-0000-4000-8000-000000000002"}' | jq -r .id)
  curl -s -o /dev/null -X POST $B/api/demo/orders/$o/simulate-payment; curl -s -X POST $B/api/ops/orders/$o/release | jq -r .id; }

say "1. Start: the request records the intent; the worker delivers it within ~1 s"
JOB1=$(new_job)
curl -s -X POST $B/api/ops/jobs/$JOB1/attempts -H "$J" -d "{\"printerId\":\"$P\"}" | jq -c '{status, attempt: .attempts[-1].status}'
A1=$(curl -s $B/api/ops/jobs/$JOB1 | jq -r '.attempts[-1].id')
sleep 2; echo "after 2 s: $(job_status $JOB1)"
echo "simulator: $(curl -s $S/printers/$P | jq -c '{runningAttemptId, bedState}')"

say "2. Simulator restart while printing (docker compose restart simulator)"
docker compose restart simulator >/dev/null 2>&1
for i in $(seq 1 40); do curl -s $S/health >/dev/null 2>&1 && break; sleep 0.5; done
echo "after restart, simulator journal: $(curl -s $S/printers/$P | jq -c '{runningAttemptId, bedState}')"
echo "backend: $(job_status $JOB1)"

say "3. Printer finishes (simulator dev control); the observer records it; bed stays occupied"
curl -s -X POST $S/dev/printers/$P/complete >/dev/null; sleep 2
echo "backend: $(job_status $JOB1)"
PRINTER=$(curl -s $B/api/ops/printers | jq -c --arg p "$P" '.[] | select(.id==$p) | {bedState, bedCycleAttemptId, currentAttemptId}')
echo "printer: $PRINTER"

say "4. QC passed; bed cleared by naming the print on it"
curl -s -X POST $B/api/ops/attempts/$A1/qc -H "$J" -d '{"result":"PASSED"}' | jq -c '{status}'
curl -s -X POST $B/api/ops/printers/$P/clear-bed -H "$J" -d "{\"attemptId\":\"$A1\"}" | jq -c '{bedState, bedCycleAttemptId}'

say "5. Lost acknowledgement: the simulator starts the print but the reply is dropped"
curl -s -X POST $S/dev/printers/$P/faults -H "$J" -d '{"dropNextStartAck": true}' >/dev/null
JOB2=$(new_job); JOB3=$(new_job)
curl -s -o /dev/null -X POST $B/api/ops/jobs/$JOB2/attempts -H "$J" -d "{\"printerId\":\"$P\"}"
SEEN=""; for i in $(seq 1 30); do s=$(curl -s $B/api/ops/jobs/$JOB2 | jq -r '.attempts[-1].status'); case " $SEEN " in *" $s "*) ;; *) SEEN="$SEEN $s"; echo "attempt status: $s"
  if [ "$s" = RECONCILIATION_REQUIRED ]; then echo "  another job on the same printer meanwhile: $(curl -s -X POST $B/api/ops/jobs/$JOB3/attempts -H "$J" -d "{\"printerId\":\"$P\"}" | jq -c '{status: .status, code: .code}')"; fi;; esac
  [ "$s" = RUNNING ] && break; sleep 0.3; done
A2=$(curl -s $B/api/ops/jobs/$JOB2 | jq -r '.attempts[-1].id')
CMD2=$(docker compose exec -T db psql -U one3d -d one3d -Atc "select id from printer_command where attempt_id = '$A2'")
echo "simulator journal for that command: $(curl -s $S/commands/$CMD2 | jq -c '{outcome, phase}')"
echo "attempts of job 2: $(job_status $JOB2)"

say "6. A delayed clearance for an earlier print cannot clear the current one"
curl -s -X POST $S/dev/printers/$P/fail -H "$J" -d '{"reason":"First layer detached"}' >/dev/null; sleep 2
echo "job 2: $(job_status $JOB2)"
echo "clear naming print 1 (stale): $(curl -s -X POST $B/api/ops/printers/$P/clear-bed -H "$J" -d "{\"attemptId\":\"$A1\"}" | jq -c '{status, code}')"
echo "clear naming print 2:         $(curl -s -X POST $B/api/ops/printers/$P/clear-bed -H "$J" -d "{\"attemptId\":\"$A2\"}" | jq -c '{bedState}')"

say "7. Two operators start different jobs on the same printer at the same moment"
JOB4=$(new_job); JOB5=$(new_job)
( curl -s -o /dev/null -w "job 4: HTTP %{http_code}\n" -X POST $B/api/ops/jobs/$JOB4/attempts -H "$J" -d "{\"printerId\":\"$P\"}" & \
  curl -s -o /dev/null -w "job 5: HTTP %{http_code}\n" -X POST $B/api/ops/jobs/$JOB5/attempts -H "$J" -d "{\"printerId\":\"$P\"}" & wait )
sleep 2; echo "active attempts on the printer: $(docker compose exec -T db psql -U one3d -d one3d -Atc "select count(*) from print_attempt where printer_id = '$P' and status in ('START_INTENT','RUNNING','RECONCILIATION_REQUIRED')")"

say "8. Clean up: finish the running print and clear the bed"
RUN=$(curl -s $B/api/ops/printers | jq -r --arg p "$P" '.[] | select(.id==$p) | .currentAttemptId')
curl -s -X POST $S/dev/printers/$P/complete >/dev/null; sleep 2
curl -s -X POST $B/api/ops/printers/$P/clear-bed -H "$J" -d "{\"attemptId\":\"$RUN\"}" | jq -c '{bedState}'
