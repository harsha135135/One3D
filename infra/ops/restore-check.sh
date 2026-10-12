#!/bin/bash
# Backup and restore exercise (Checkpoint 8). Takes a fresh backup, restores it
# into a separate database on the same PostgreSQL server, compares row counts
# and one order with everything it links to against the live database, checks
# that a restored file record still names its exact stored version (same
# SHA-256), then drops the copy. The live database and the application are not
# changed. To restore the live database itself, see infra/README.md.
#   infra/ops/restore-check.sh          the AWS demo (deployment user; runs on the instance through SSM)
#   infra/ops/restore-check.sh --local  the local Compose stack (storage on 127.0.0.1:9090)
set -euo pipefail
HERE=$(cd "$(dirname "$0")" && pwd)
if [ "${1:-}" = --local ]; then
  exec bash "$HERE/restore-check-host.sh" local http://127.0.0.1:9090 one3d-artifacts
fi

export AWS_PROFILE=${AWS_PROFILE:-one3d-deployer} AWS_REGION=${AWS_REGION:-ap-south-1} AWS_PAGER=""
TF="$HERE/../terraform/demo"
INSTANCE=$(terraform -chdir="$TF" output -raw instance_id)
OPS=$(terraform -chdir="$TF" output -raw ops_bucket)
ARTIFACTS=$(terraform -chdir="$TF" output -raw artifacts_bucket)
[ "$(aws ec2 describe-instances --instance-ids "$INSTANCE" --query 'Reservations[0].Instances[0].State.Name' \
  --output text)" = running ] || { echo "The demo is not running (infra/ops/demo.sh start)" >&2; exit 1; }

# The host script travels inside the command, so the instance needs nothing new.
script=$(base64 < "$HERE/restore-check-host.sh" | tr -d '\n')
id=$(aws ssm send-command --instance-ids "$INSTANCE" --document-name AWS-RunShellScript \
  --comment "One3D restore check" --parameters "$(jq -n --arg s "$script" --arg o "$OPS" --arg a "$ARTIFACTS" \
    --arg r "$AWS_REGION" '{commands: [
      "echo \($s) | base64 -d > /tmp/one3d-restore-check.sh",
      "bash /tmp/one3d-restore-check.sh aws \($o) \($a) \($r); status=$?; rm -f /tmp/one3d-restore-check.sh; exit $status"
    ], executionTimeout: ["600"]}')" --query Command.CommandId --output text)
for i in $(seq 1 120); do
  status=$(aws ssm get-command-invocation --command-id "$id" --instance-id "$INSTANCE" --query Status --output text \
    2>/dev/null || echo Pending)
  case $status in Pending|InProgress|Delayed) sleep 5 ;; *) break ;; esac
done
aws ssm get-command-invocation --command-id "$id" --instance-id "$INSTANCE" \
  --query '[StandardOutputContent, StandardErrorContent]' --output text
[ "$status" = Success ]
