#!/bin/bash
# Checkpoint 7: deploy a known revision, revert to the previous one and go
# forward again with "Deploy a commit" (deploy.yml), as the runbook describes.
# A green workflow run is not taken as proof. The two revisions differ in
# Nginx's Content-Security-Policy (script-src), so after each deploy the live
# header, Nginx's rendered config and the instance's deployed-tag/previous-tag
# files (read through SSM) show what is actually serving.
#   STARTED=<when demo.sh start began, UTC> rollback.sh <newer sha> <older sha>
# Run as the deployment user, with gh signed in to the repository.
set -euo pipefail
export AWS_PROFILE=${AWS_PROFILE:-one3d-deployer} AWS_REGION=ap-south-1 AWS_PAGER=""
NEW=${1:?newer sha} OLD=${2:?older sha}
ROOT=$(cd "$(dirname "$0")/../../../.." && pwd)
I=$(terraform -chdir="$ROOT/infra/terraform/demo" output -raw instance_id)

on_instance() {
  local id status
  id=$(aws ssm send-command --instance-ids "$I" --document-name AWS-RunShellScript \
    --parameters "$(jq -n --arg c "$1" '{commands: [$c]}')" --query Command.CommandId --output text)
  for i in $(seq 1 30); do
    status=$(aws ssm get-command-invocation --command-id "$id" --instance-id "$I" --query Status --output text 2>/dev/null || echo Pending)
    case $status in Pending|InProgress|Delayed) sleep 2 ;; *) break ;; esac
  done
  aws ssm get-command-invocation --command-id "$id" --instance-id "$I" --query StandardOutputContent --output text
}
serving() {
  echo "  instance: $(on_instance 'echo deployed $(cut -c1-7 /opt/one3d/deployed-tag), previous $(cut -c1-7 /opt/one3d/previous-tag 2>/dev/null || echo none)')"
  # Only the script-src directive: img-src allowed cdn.razorpay.com in both revisions.
  local script_src
  script_src=$(curl -sI https://demo.one3d.store/ | grep -i '^content-security-policy:' | grep -oE "script-src [^;]*" || true)
  echo "  live $script_src"
  echo "  in the instance's rendered Nginx config: $(on_instance 'docker exec one3d-nginx-1 grep -oE "script-src [^;]*" /etc/nginx/conf.d/default.conf')"
  echo "  Nginx container started: $(on_instance 'docker inspect -f {{.State.StartedAt}} one3d-nginx-1' | cut -c12-19) UTC"
  echo "  site: HTTP $(curl -s -o /dev/null -w '%{http_code}' https://demo.one3d.store/api/catalog/configurations)"
}
# watch <sha> <since>: wait for the "Deploy a commit" run for that SHA created after <since> and summarize it.
watch() {
  local run
  for i in $(seq 1 30); do
    run=$(gh run list --workflow deploy.yml --branch main --event workflow_dispatch --limit 5 --json databaseId,createdAt \
      --jq ".[] | select(.createdAt >= \"$2\") | .databaseId" | while read -r r; do
        [[ $(gh run view "$r" --json jobs --jq ".jobs[0].name") == *"$1"* ]] && { echo "$r"; break; }; done || true)
    [ -n "$run" ] && break; sleep 5
  done
  gh run watch "$run" > /dev/null 2>&1 || true
  while [ "$(gh run view "$run" --json status --jq .status)" != completed ]; do sleep 10; done
  gh run view "$run" --json conclusion,createdAt,updatedAt --jq "\"  run $run: \(.conclusion) (\(.createdAt[11:19])–\(.updatedAt[11:19]) UTC)\""
  gh run view "$run" --log 2>/dev/null | grep -E "Deployed [0-9a-f]{40}|Deploy status:" | sed -E 's/^.*Z /  /'
}

echo "## 1. The two revisions"
for repo in one3d-backend one3d-frontend one3d-simulator; do
  echo "  $repo: ${NEW:0:7} $(aws ecr describe-images --repository-name $repo --image-ids imageTag=$NEW --query 'imageDetails[0].imageDigest' --output text | cut -c1-19)…," \
    "${OLD:0:7} $(aws ecr describe-images --repository-name $repo --image-ids imageTag=$OLD --query 'imageDetails[0].imageDigest' --output text | cut -c1-19)…"
done
echo "  database migrations changed between them: $(git -C "$ROOT" diff --name-only $OLD $NEW -- backend/src/main/resources/db/migration | wc -l | tr -d ' ')"
echo "  files of the deploy bundle changed between them: $(git -C "$ROOT" diff --name-only $OLD $NEW -- compose.yaml compose.deploy.yaml infra/nginx/bootstrap infra/nginx/https infra/deploy | tr '\n' ' ')"

echo; echo "## 2. ${NEW:0:7}, deployed by 'demo.sh start' (the newest passing commit on main)"
watch "$NEW" "${STARTED:?STARTED=<time demo.sh start began, UTC ISO>}"
serving

echo; echo "## 3. Roll back: Deploy a commit with ${OLD:0:7}"
since=$(date -u +%FT%TZ); gh workflow run deploy.yml --ref main -f sha="$OLD"; sleep 5
watch "$OLD" "$since"
serving

echo; echo "## 4. Forward again: Deploy a commit with ${NEW:0:7}"
since=$(date -u +%FT%TZ); gh workflow run deploy.yml --ref main -f sha="$NEW"; sleep 5
watch "$NEW" "$since"
serving
