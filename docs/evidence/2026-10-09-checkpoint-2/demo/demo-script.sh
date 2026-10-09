#!/bin/bash
# Checkpoint 2 local demonstration against the seeded simulated printer.
B=http://127.0.0.1:8080
P=0d3d0000-0000-4000-8000-000000000101
call() { # method url [json]
  echo "\$ curl -X $1 $2${3:+ -d '$3'}"
  if [ -n "$3" ]; then R=$(curl -s -X "$1" "$2" -H 'Content-Type: application/json' -d "$3" -w '\n%{http_code}');
  else R=$(curl -s -X "$1" "$2" -w '\n%{http_code}'); fi
  CODE=$(echo "$R" | tail -1); BODY=$(echo "$R" | sed '$d')
  echo "HTTP $CODE"; echo "$BODY" | jq -c "${JQ:-.}"; echo
}
JQ='{id,status,confirmationSource}' call POST $B/api/orders '{"productConfigurationId":"0d3d0000-0000-4000-8000-000000000002","quantity":1}'
ORDER=$(echo "$BODY" | jq -r .id)
JQ='{status,confirmedAt,confirmationSource}' call POST $B/api/demo/orders/$ORDER/simulate-payment
JQ='[.[] | {name,simulated,bedState,currentAttemptId}]' call GET $B/api/ops/printers
JQ='{id,status,recipeRevisionId,attempts}' call POST $B/api/ops/orders/$ORDER/release
JOB=$(echo "$BODY" | jq -r .id)
JA='{status, attempts: [.attempts[] | {attemptNumber,status,failureReason,qcResult}]}'
JQ="$JA" call POST $B/api/ops/jobs/$JOB/attempts "{\"printerId\":\"$P\"}"
A1=$(echo "$BODY" | jq -r '.attempts[-1].id')
JQ="$JA" call POST $B/api/ops/attempts/$A1/simulate-failure '{"reason":"First layer detached"}'
JQ=".[] | select(.id==\"$P\") | {bedState,currentAttemptId}" call GET $B/api/ops/printers
JQ='{code,detail}' call POST $B/api/ops/jobs/$JOB/attempts "{\"printerId\":\"$P\"}"
JQ='{bedState,currentAttemptId}' call POST $B/api/ops/printers/$P/clear-bed
JQ="$JA" call POST $B/api/ops/jobs/$JOB/attempts "{\"printerId\":\"$P\"}"
A2=$(echo "$BODY" | jq -r '.attempts[-1].id')
JQ="$JA" call POST $B/api/ops/attempts/$A2/simulate-completion
JQ=".[] | select(.id==\"$P\") | {bedState,currentAttemptId}" call GET $B/api/ops/printers
JQ='{code,detail}' call POST $B/api/ops/orders/$ORDER/ready-for-handoff
JQ="$JA" call POST $B/api/ops/attempts/$A2/qc '{"result":"PASSED","notes":"Whole part inspected"}'
JQ='{status,readyForHandoffAt}' call POST $B/api/ops/orders/$ORDER/ready-for-handoff
JQ='{bedState,currentAttemptId}' call POST $B/api/ops/printers/$P/clear-bed
echo "ORDER=$ORDER JOB=$JOB"
