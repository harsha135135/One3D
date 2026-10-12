#!/bin/bash
# Run by GitHub Actions (with the OIDC deploy role): unpack one commit's
# deploy bundle on the demo instance and run deploy.sh there, through SSM Run
# Command (no SSH). Prints the instance's output and fails if the deploy fails.
#   remote-deploy.sh <commit sha>
# Needs AWS_REGION, DEMO_INSTANCE_ID and OPS_BUCKET in the environment.
set -euo pipefail
SHA=${1:?usage: remote-deploy.sh <commit sha>}
[[ $SHA =~ ^[0-9a-f]{40}$ ]] || { echo "Not a commit SHA: $SHA" >&2; exit 2; }
: "${AWS_REGION:?}" "${DEMO_INSTANCE_ID:?}" "${OPS_BUCKET:?}"

commands=$(jq -n --arg sha "$SHA" --arg bucket "$OPS_BUCKET" --arg region "$AWS_REGION" '[
  "set -eu",
  "aws s3 cp s3://\($bucket)/deploy/\($sha).tar.gz /tmp/one3d-\($sha).tar.gz --region \($region) --only-show-errors",
  "rm -rf /opt/one3d/app.new && mkdir -p /opt/one3d/app.new",
  "tar -xzf /tmp/one3d-\($sha).tar.gz -C /opt/one3d/app.new && rm -f /tmp/one3d-\($sha).tar.gz",
  "rm -rf /opt/one3d/app.old; if [ -d /opt/one3d/app ]; then mv /opt/one3d/app /opt/one3d/app.old; fi",
  "mv /opt/one3d/app.new /opt/one3d/app",
  "bash /opt/one3d/app/infra/deploy/deploy.sh \($sha)"
]')

id=$(aws ssm send-command --region "$AWS_REGION" --instance-ids "$DEMO_INSTANCE_ID" \
  --document-name AWS-RunShellScript --comment "One3D deploy ${SHA:0:12}" --timeout-seconds 600 \
  --parameters "$(jq -n --argjson c "$commands" '{commands: $c, executionTimeout: ["900"]}')" \
  --query Command.CommandId --output text)
echo "SSM command $id"

status=Pending
for i in $(seq 1 120); do
  status=$(aws ssm get-command-invocation --region "$AWS_REGION" --command-id "$id" \
    --instance-id "$DEMO_INSTANCE_ID" --query Status --output text 2>/dev/null || echo Pending)
  case $status in Pending|InProgress|Delayed) sleep 10 ;; *) break ;; esac
done
aws ssm get-command-invocation --region "$AWS_REGION" --command-id "$id" --instance-id "$DEMO_INSTANCE_ID" \
  --query '[StandardOutputContent, StandardErrorContent]' --output text
echo "Deploy status: $status"
[ "$status" = Success ]
