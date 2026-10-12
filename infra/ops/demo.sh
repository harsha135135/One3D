#!/bin/bash
# Start, stop or check the on-demand demo, from a machine signed in as the
# deployment user (aws login --profile one3d-deployer). No SSH: commands run
# on the instance through SSM.
#   infra/ops/demo.sh status
#   infra/ops/demo.sh stop    back up the database to S3, stop the containers
#                             cleanly, then stop the instance
#   infra/ops/demo.sh start   start the instance, redeploy the deployed commit
#                             (renews the certificate if due) and check the site;
#                             then deploy the latest passing commit on main if newer
# While stopped, only the disks, the Elastic IP and stored images/files cost money.
set -euo pipefail
export AWS_PROFILE=${AWS_PROFILE:-one3d-deployer} AWS_REGION=${AWS_REGION:-ap-south-1} AWS_PAGER=""
TF=$(cd "$(dirname "$0")/../terraform/demo" && pwd)
out() { terraform -chdir="$TF" output -raw "$1"; }
INSTANCE=$(out instance_id)
OPS_BUCKET=$(out ops_bucket)
DOMAIN=demo.one3d.store

state() { aws ec2 describe-instances --instance-ids "$INSTANCE" --query 'Reservations[0].Instances[0].State.Name' --output text; }

# run_on_instance <description> <command...>: one SSM Run Command, waits, prints output.
run_on_instance() {
  local what=$1; shift
  local params id status
  params=$(jq -n --args '{commands: $ARGS.positional, executionTimeout: ["900"]}' "$@")
  id=$(aws ssm send-command --instance-ids "$INSTANCE" --document-name AWS-RunShellScript \
    --comment "One3D demo.sh: $what" --parameters "$params" --query Command.CommandId --output text)
  for i in $(seq 1 90); do
    status=$(aws ssm get-command-invocation --command-id "$id" --instance-id "$INSTANCE" --query Status --output text 2>/dev/null || echo Pending)
    case $status in Pending|InProgress|Delayed) sleep 10 ;; *) break ;; esac
  done
  aws ssm get-command-invocation --command-id "$id" --instance-id "$INSTANCE" \
    --query '[StandardOutputContent, StandardErrorContent]' --output text
  [ "$status" = Success ] || { echo "$what failed: $status" >&2; exit 1; }
}

wait_for_ssm() {
  for i in $(seq 1 60); do
    [ "$(aws ssm describe-instance-information --filters "Key=InstanceIds,Values=$INSTANCE" \
      --query 'InstanceInformationList[0].PingStatus' --output text)" = Online ] && return 0
    sleep 10
  done
  echo "The instance did not come online in SSM" >&2; exit 1
}

case ${1:-status} in
  status)
    echo "instance: $(state)"
    curl -s -o /dev/null -w "https://$DOMAIN/api/catalog/configurations: HTTP %{http_code}\n" --max-time 10 \
      "https://$DOMAIN/api/catalog/configurations" || true
    ;;
  stop)
    [ "$(state)" = running ] || { echo "Already $(state)"; exit 0; }
    stamp=$(date -u +%Y%m%dT%H%M%SZ)
    run_on_instance "backup and stop containers" \
      "set -e" \
      "cd /opt/one3d/app" \
      "docker compose --env-file /opt/one3d/one3d.env -f compose.yaml -f compose.deploy.yaml exec -T db pg_dump -U one3d -d one3d --format=custom | aws s3 cp - s3://$OPS_BUCKET/backups/one3d-$stamp.dump --only-show-errors" \
      "echo backup: s3://$OPS_BUCKET/backups/one3d-$stamp.dump" \
      "docker compose --env-file /opt/one3d/one3d.env -f compose.yaml -f compose.deploy.yaml stop"
    aws ec2 stop-instances --instance-ids "$INSTANCE" --output text > /dev/null
    aws ec2 wait instance-stopped --instance-ids "$INSTANCE"
    echo "Stopped. Data stays on the data volume and in S3."
    ;;
  start)
    if [ "$(state)" != running ]; then
      aws ec2 start-instances --instance-ids "$INSTANCE" --output text > /dev/null
      aws ec2 wait instance-running --instance-ids "$INSTANCE"
    fi
    wait_for_ssm
    run_on_instance "redeploy the deployed commit" \
      "set -e" "bash /opt/one3d/app/infra/deploy/deploy.sh \$(cat /opt/one3d/deployed-tag)"
    deployed=$(curl -s --max-time 10 "https://$DOMAIN/api/catalog/configurations" -o /dev/null -w '%{http_code}')
    echo "Site: HTTP $deployed"
    # A push to main while the demo was stopped was skipped; deploy it now.
    latest=$(gh run list --workflow ci.yml --branch main --event push --status success --limit 1 --json headSha --jq '.[0].headSha')
    current=$(aws ssm get-command-invocation --command-id "$(aws ssm send-command --instance-ids "$INSTANCE" \
      --document-name AWS-RunShellScript --parameters 'commands=["cat /opt/one3d/deployed-tag"]' \
      --query Command.CommandId --output text)" --instance-id "$INSTANCE" --query StandardOutputContent --output text 2>/dev/null || true)
    if [ -n "$latest" ] && [ "$latest" != "$(echo "$current" | tr -d '[:space:]')" ]; then
      echo "Deploying the latest passing commit on main: ${latest:0:12}"
      gh workflow run deploy.yml --ref main -f sha="$latest"
    fi
    ;;
  *) echo "usage: demo.sh status|start|stop" >&2; exit 2 ;;
esac
