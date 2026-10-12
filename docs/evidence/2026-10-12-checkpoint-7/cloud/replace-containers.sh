#!/bin/bash
# Checkpoint 7: on the instance (through SSM, no SSH), record memory use, then
# recreate every container from its image and show that the database rows are
# still there afterwards (they live on the data volume). Run as the deployment user.
set -euo pipefail
export AWS_PROFILE=${AWS_PROFILE:-one3d-deployer} AWS_REGION=ap-south-1 AWS_PAGER=""
I=$(terraform -chdir="$(dirname "$0")/../../../../infra/terraform/demo" output -raw instance_id)
run() {
  local params id status
  params=$(jq -n --args '{commands: $ARGS.positional, executionTimeout: ["900"]}' "$@")
  id=$(aws ssm send-command --instance-ids "$I" --document-name AWS-RunShellScript --parameters "$params" --query Command.CommandId --output text)
  for i in $(seq 1 90); do
    status=$(aws ssm get-command-invocation --command-id "$id" --instance-id "$I" --query Status --output text 2>/dev/null || echo Pending)
    case $status in Pending|InProgress|Delayed) sleep 5 ;; *) break ;; esac
  done
  aws ssm get-command-invocation --command-id "$id" --instance-id "$I" --query StandardOutputContent --output text
  [ "$status" = Success ]
}
C='docker compose --env-file /opt/one3d/one3d.env -f compose.yaml -f compose.deploy.yaml'
COUNTS="$C exec -T db psql -U one3d -d one3d -At -c \"select 'orders=' || count(*) from customer_order union all select 'artifacts=' || count(*) from artifact union all select 'facts=' || count(*) from order_fact\" | tr '\\n' ' '; echo"

echo "## 1. The running stack and its memory (t3.small, 2 GiB)"
run "cd /opt/one3d/app" "echo deployed: \$(cut -c1-12 /opt/one3d/deployed-tag)" "$C ps --format '{{.Service}} {{.Status}} {{.Ports}}'" \
  "free -m | head -2" "docker stats --no-stream --format '{{.Name}} {{.MemUsage}} {{.CPUPerc}}'" \
  "df -h / /opt/one3d/data | tail -2" "echo rows before: \$($COUNTS)"

echo "## 2. Recreate every container from its image"
run "cd /opt/one3d/app" "ONE3D_NGINX_MODE=https $C up -d --no-build --force-recreate 2>&1 | grep -E 'Recreated|Started' | sort | uniq -c | sed 's/^ *//'" \
  "for i in \$(seq 1 60); do curl -fsS -o /dev/null --resolve demo.one3d.store:443:127.0.0.1 https://demo.one3d.store/api/catalog/configurations && break; sleep 3; done" \
  "$C ps --format '{{.Service}} {{.Status}}'" "echo rows after: \$($COUNTS)"
