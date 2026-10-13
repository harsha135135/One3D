#!/bin/bash
# Recreate the disposable part of the demo (the EC2 instance) with Terraform,
# without touching the data: the data volume, buckets, images, Cognito and the
# Elastic IP stay. First used in Checkpoint 8 (the copy that ran is in
# docs/evidence/2026-10-12-checkpoint-8/cloud/); here every apply goes through a
# saved plan that is checked before it is applied. As the deployment user, one
# phase at a time, with PLAN_FILE outside the repository:
#   replace-instance.sh prepare  backup (demo.sh stop), data-volume snapshot, protection off,
#                                a saved plan, and a check that it deletes nothing but the
#                                instance and its volume attachment
#   replace-instance.sh apply    apply that plan, wait for the new instance, check the data
#                                volume was mounted as it was, point GitHub at the new
#                                instance, protection on again
#   replace-instance.sh deploy   deploy the current main commit with "Deploy a commit"
set -euo pipefail
export AWS_PROFILE=${AWS_PROFILE:-one3d-deployer} AWS_REGION=ap-south-1 AWS_PAGER=""
ROOT=$(cd "$(dirname "$0")/../../../.." && pwd)
TF="$ROOT/infra/terraform/demo"
# The saved plan holds sensitive values; keep it outside the repository.
PLAN=${PLAN_FILE:?set PLAN_FILE to a path outside the repository}
tf() { terraform -chdir="$TF" "$@"; }
out() { tf output -raw "$1"; }
say() { echo; echo "## $* ($(date -u +%T) UTC)"; }
# protection true|false: change only the instance's termination protection,
# through a saved plan that may change nothing else.
protection() {
  tf plan -input=false -no-color -target=aws_instance.demo -var termination_protection="$1" -out="$PLAN.protection" > /dev/null
  local changes
  changes=$(tf show -json "$PLAN.protection" | jq -r '.resource_changes[] | select(.change.actions != ["no-op"]) | . as $r
    | ($r.change.before // {}) as $b
    | "\($r.address) \($r.change.actions | join(",")) " + ([$r.change.after | to_entries[] | select($b[.key] != .value) | .key] | join(","))')
  if [ "$changes" != "aws_instance.demo update disable_api_termination" ]; then
    rm -f "$PLAN.protection"
    echo "REFUSED: the plan would change more than termination protection: $changes" >&2; exit 1
  fi
  tf apply -input=false -no-color "$PLAN.protection" | grep -E "Modif|Apply complete" | grep -v Still
  rm -f "$PLAN.protection"
  # AWS can report the old value for about a minute.
  for i in $(seq 1 12); do
    [ "$(aws ec2 describe-instance-attribute --instance-id "$(out instance_id)" --attribute disableApiTermination \
      --query DisableApiTermination.Value --output text)" = "$([ "$1" = true ] && echo True || echo False)" ] && break
    sleep 10
  done
  echo "disableApiTermination: $(aws ec2 describe-instance-attribute --instance-id "$(out instance_id)" \
    --attribute disableApiTermination --query DisableApiTermination.Value --output text)"
}
describe() {
  aws ec2 describe-instances --instance-ids "$1" --query 'Reservations[0].Instances[0].[InstanceId,State.Name,LaunchTime,ImageId,PublicIpAddress]' --output text
}

case ${1:?prepare, apply or deploy} in
prepare)
  OLD=$(out instance_id)
  VOLUME=$(out data_volume_id)
  say "Before: the instance, its data volume and the address"
  echo "instance: $(describe "$OLD")"
  aws ec2 describe-volumes --volume-ids "$VOLUME" --query 'Volumes[0].[VolumeId,Size,State,Attachments[0].InstanceId]' --output text
  say "1. Back up and stop (demo.sh stop)"
  "$ROOT/infra/ops/demo.sh" stop 2>&1 | grep -E "backup:|Stopped\."
  say "2. Snapshot the data volume (a second copy while the instance is replaced)"
  snap=$(aws ec2 create-snapshot --volume-id "$VOLUME" --description "one3d demo data before instance replacement" \
    --tag-specifications 'ResourceType=snapshot,Tags=[{Key=Project,Value=one3d},{Key=Name,Value=one3d-demo-data-before-replacement}]' \
    --query SnapshotId --output text)
  aws ec2 wait snapshot-completed --snapshot-ids "$snap"
  echo "snapshot $snap: $(aws ec2 describe-snapshots --snapshot-ids "$snap" --query 'Snapshots[0].State' --output text)"
  say "3. Termination protection off (an in-place change, checked first)"
  protection false
  say "4. The plan to replace the instance, saved"
  tf plan -input=false -no-color -replace=aws_instance.demo -var termination_protection=false -out="$PLAN" \
    | grep -E "^  # |must be replaced|Plan:"
  say "5. Guard: the saved plan deletes only the instance and its volume attachment"
  deletes=$(tf show -json "$PLAN" | jq -r '.resource_changes[] | select(.change.actions | index("delete")) | .address')
  sed 's/^/  deleted (and created again): /' <<< "$deletes"
  if grep -vxE 'aws_instance\.demo|aws_volume_attachment\.data' <<< "$deletes"; then
    echo "REFUSED: the plan deletes something other than the instance" >&2; exit 1
  fi
  echo "  data volume, buckets, images, Cognito, parameters and the Elastic IP: not deleted"
  ;;
apply)
  OLD=$(out instance_id)
  say "6. Apply the saved plan"
  tf apply -input=false -no-color "$PLAN" | grep -E "Destroy|Creat|Modif|Apply complete" | grep -vE "Still"
  NEW=$(out instance_id)
  echo "old instance $OLD: $(aws ec2 describe-instances --instance-ids "$OLD" --query 'Reservations[0].Instances[0].State.Name' --output text)"
  echo "new instance: $(describe "$NEW")"
  say "7. Wait for the first boot (SSM online and the boot script finished)"
  for i in $(seq 1 60); do
    [ "$(aws ssm describe-instance-information --filters "Key=InstanceIds,Values=$NEW" \
      --query 'InstanceInformationList[0].PingStatus' --output text)" = Online ] && break
    sleep 10
  done
  id=$(aws ssm send-command --instance-ids "$NEW" --document-name AWS-RunShellScript --parameters "$(jq -n '{commands: [
    "cloud-init status --wait > /dev/null; cloud-init status",
    "findmnt -no SOURCE,FSTYPE /opt/one3d/data",
    "ls /opt/one3d/data",
    "echo postgres files: $(sudo find /opt/one3d/data/postgres -maxdepth 1 | wc -l), PG_VERSION $(sudo cat /opt/one3d/data/postgres/PG_VERSION)",
    "echo certificate: $(sudo ls /opt/one3d/data/letsencrypt/live 2>/dev/null)",
    "docker --version; docker compose version --short; swapon --show=NAME,SIZE --noheadings"
  ]}')" --query Command.CommandId --output text)
  sleep 30
  for i in $(seq 1 30); do
    status=$(aws ssm get-command-invocation --command-id "$id" --instance-id "$NEW" --query Status --output text 2>/dev/null || echo Pending)
    case $status in Pending|InProgress|Delayed) sleep 10 ;; *) break ;; esac
  done
  aws ssm get-command-invocation --command-id "$id" --instance-id "$NEW" --query StandardOutputContent --output text | sed 's/^/  /'
  say "8. GitHub deploys to the new instance"
  gh variable set DEMO_INSTANCE_ID --body "$NEW" > /dev/null
  echo "DEMO_INSTANCE_ID: $(gh variable list | awk '$1 == "DEMO_INSTANCE_ID" {print $2}')"
  say "9. Termination protection on again (checked first)"
  protection true
  rm -f "$PLAN"
  ;;
deploy)
  git -C "$ROOT" fetch -q origin main
  SHA=$(git -C "$ROOT" rev-parse origin/main)
  say "10. Deploy main ($SHA) to the new instance"
  since=$(date -u +%FT%TZ)
  gh workflow run deploy.yml --ref main -f sha="$SHA"
  sleep 10
  run=$(gh run list --workflow deploy.yml --event workflow_dispatch --limit 3 --json databaseId,createdAt \
    --jq ".[] | select(.createdAt >= \"$since\") | .databaseId" | head -1)
  while [ "$(gh run view "$run" --json status --jq .status)" != completed ]; do sleep 10; done
  echo "run $run: $(gh run view "$run" --json conclusion --jq .conclusion)"
  gh run view "$run" --log 2>/dev/null | grep -E "Deployed [0-9a-f]{40}|Certificate not yet due|Successfully received certificate|Deploy status" | sed -E 's/^.*Z /  /'
  echo "site: HTTP $(curl -s -o /dev/null -w '%{http_code}' https://demo.one3d.store/api/catalog/configurations)"
  ;;
esac
