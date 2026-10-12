#!/bin/bash
# Checkpoint 8 on the AWS demo: follow real requests from Nginx's access log to
# the backend's JSON log lines by their request id. Uses Razorpay's webhook
# deliveries since <since> (the backend logs each outcome).
#   request-correlation.sh <since, UTC ISO time>
set -uo pipefail
export AWS_PROFILE=${AWS_PROFILE:-one3d-deployer} AWS_REGION=ap-south-1 AWS_PAGER=""
ROOT=$(cd "$(dirname "$0")/../../../.." && pwd)
I=$(terraform -chdir="$ROOT/infra/terraform/demo" output -raw instance_id)
SINCE=${1:?since}
read -r -d '' REMOTE <<EOF
docker logs --since $SINCE one3d-nginx-1 2>&1 | grep 'POST /api/payments/razorpay/webhook' | while read -r line; do
  rid=\${line##*rid=}
  echo "nginx:   \$(echo "\$line" | sed -E 's/^[0-9a-f.:]+ /<client> /')"
  docker logs --since $SINCE one3d-backend-1 2>&1 | grep "\"request_id\":\"\$rid\"" | python3 -c 'import json,re,sys
for d in map(json.loads, sys.stdin):
    message = re.sub(r"\\b(pay|order|evt)_[A-Za-z0-9]+", r"\\1_…", d["message"])
    print("backend: " + json.dumps({"level": d["log"]["level"], "request_id": d["request_id"], "message": message}, ensure_ascii=False))'
  echo
done
EOF
id=$(aws ssm send-command --instance-ids "$I" --document-name AWS-RunShellScript \
  --parameters "$(jq -n --arg c "$REMOTE" '{commands: [$c]}')" --query Command.CommandId --output text)
for i in $(seq 1 30); do
  status=$(aws ssm get-command-invocation --command-id "$id" --instance-id "$I" --query Status --output text 2>/dev/null || echo Pending)
  case $status in Pending|InProgress|Delayed) sleep 2 ;; *) break ;; esac
done
aws ssm get-command-invocation --command-id "$id" --instance-id "$I" --query StandardOutputContent --output text
