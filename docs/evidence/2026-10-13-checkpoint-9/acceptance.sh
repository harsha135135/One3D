#!/bin/bash
# Checkpoint 9: the acceptance run against one known revision on the AWS demo.
# The demo must be running with that revision deployed (infra/ops/demo.sh
# start). Run as the deployment user, with gh signed in:
#   acceptance.sh <full revision sha>
# Each part writes its own file next to this script; this prints a summary.
set -uo pipefail
export AWS_PROFILE=${AWS_PROFILE:-one3d-deployer} AWS_REGION=ap-south-1 AWS_PAGER=""
REV=${1:?full revision sha}
HERE=$(cd "$(dirname "$0")" && pwd)
ROOT=$(cd "$HERE/../../.." && pwd)
EV=$ROOT/docs/evidence
I=$(terraform -chdir="$ROOT/infra/terraform/demo" output -raw instance_id)
on_instance() {
  local id status
  id=$(aws ssm send-command --instance-ids "$I" --document-name AWS-RunShellScript \
    --parameters "$(jq -n --arg c "$1" '{commands: [$c]}')" --query Command.CommandId --output text)
  for i in $(seq 1 40); do
    status=$(aws ssm get-command-invocation --command-id "$id" --instance-id "$I" --query Status --output text 2>/dev/null || echo Pending)
    case $status in Pending|InProgress|Delayed) sleep 2 ;; *) break ;; esac
  done
  aws ssm get-command-invocation --command-id "$id" --instance-id "$I" --query StandardOutputContent --output text
}
node_part() { (cd "$ROOT/frontend" && NODE_PATH="$ROOT/frontend/node_modules" node "$@"); }
say() { echo; echo "## $* ($(date -u +%T) UTC)"; }

{
say "1. The revision under test"
echo "revision: $REV"
echo "deployed on $I: $(on_instance 'cat /opt/one3d/deployed-tag')"
for repo in one3d-backend one3d-frontend one3d-simulator; do
  echo "  $repo:$REV $(aws ecr describe-images --repository-name $repo --image-ids imageTag=$REV --query 'imageDetails[0].imageDigest' --output text | cut -c1-19)…"
done
run=$(gh run list --workflow ci.yml --branch main --event push --json databaseId,headSha,conclusion \
  --jq ".[] | select(.headSha == \"$REV\") | \"\(.databaseId) \(.conclusion)\"" | head -1)
echo "CI on main for this revision: run $run"
gh run view "${run%% *}" --log 2>/dev/null | grep -E "Tests run: [0-9]+, Failures: [0-9]+, Errors: [0-9]+, Skipped: [0-9]+$|[0-9]+ passed \(" \
  | awk -F'\t' '{ sub(/^[0-9TZ:.-]+ /, "", $3); print "  " $1 ": " $3 }'
} > "$HERE/revision.txt" 2>&1
cat "$HERE/revision.txt"

say "2. HTTPS routing, headers, private ports and storage (Checkpoint 7 edge checks)"
"$EV/2026-10-12-checkpoint-7/cloud/edge-checks.sh" > "$HERE/edge-checks.txt" 2>&1; echo "edge checks: exit $?"

say "3. Monitoring stays private; memory (Checkpoint 8 checks)"
"$EV/2026-10-12-checkpoint-8/cloud/monitoring-checks.sh" > "$HERE/monitoring-checks.txt" 2>&1; echo "monitoring checks: exit $?"

say "4. The simulator's journal survived the instance replacement"
{ echo "# Command 1bd16179…, started during the Checkpoint 8 fault exercise on the replaced instance i-08b860e0cf0061856,"
  echo "# asked of the simulator now (its journal is on the data volume):"
  on_instance "docker run --rm --network one3d_default curlimages/curl:8.17.0 -s http://simulator:8090/commands/1bd16179-e8d7-4359-a9b1-0a44d61e1796"
} > "$HERE/simulator-journal.txt" 2>&1; cat "$HERE/simulator-journal.txt"

say "5. Access: customer refused on operator endpoints; CSRF; no session"
node_part "$HERE/access-checks.cjs" "$HERE" > /dev/null 2>&1; echo "access checks: exit $?"; cat "$HERE/access-checks.txt"

say "6. Catalog journey: pay in Checkout, produce, hand over"
node_part "$EV/2026-10-12-checkpoint-7/cloud/cloud-checks.cjs" pay "$HERE" > /dev/null 2>&1; echo "pay: exit $?"
node_part "$EV/2026-10-12-checkpoint-7/cloud/cloud-checks.cjs" journey "$HERE" > /dev/null 2>&1; echo "journey: exit $?"
tail -8 "$HERE/journey.txt"

say "7. Restore check"
"$ROOT/infra/ops/restore-check.sh" > "$HERE/restore-check.txt" 2>&1; echo "restore check: exit $?"
grep -E "identical|DIFFERENT|matches" "$HERE/restore-check.txt" | sort | uniq -c
