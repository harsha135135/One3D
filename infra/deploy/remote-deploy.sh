#!/bin/bash
# Owner-operated promotion, after explicit approval. Never run by CI.
#   AWS_PROFILE=one3d-deployer bash infra/deploy/remote-deploy.sh release.json --approve <commit>
# Uses existing owner IAM access. It does not start EC2, alter DNS, restore data,
# or create a release tag. The operations revision must be committed and reviewed.
set -euo pipefail
MANIFEST=${1:?usage: remote-deploy.sh <manifest.json> --approve <full SHA>}
[ "${2:-}" = --approve ] && [ -n "${3:-}" ] && [ "$#" = 3 ] || { echo "Explicit --approve <full SHA> is required" >&2; exit 2; }
SHA=$3
HERE=$(cd "$(dirname "$0")" && pwd)
python3 "$HERE/release_manifest.py" "$MANIFEST" --approve "$SHA"
# Read only validated values. Secrets are not part of the manifest.
REGION=$(jq -r .target.region "$MANIFEST")
ACCOUNT=$(jq -r .target.account_id "$MANIFEST")
INSTANCE=$(jq -r .target.instance_id "$MANIFEST")
BUCKET=$(jq -r .bundle.bucket "$MANIFEST")
KEY=$(jq -r .bundle.key "$MANIFEST")
VERSION=$(jq -r .bundle.version_id "$MANIFEST")
OPS_SHA=$(jq -r .operations_commit "$MANIFEST")
: "${AWS_PROFILE:?Set the owner AWS_PROFILE explicitly}"
export AWS_REGION=$REGION AWS_PAGER=""
[ -z "${GITHUB_ACTIONS:-}" ] || { echo "Promote from the owner's terminal, never GitHub Actions" >&2; exit 2; }
ROOT=$(git -C "$HERE" rev-parse --show-toplevel)
[ "$(git -C "$ROOT" rev-parse HEAD)" = "$OPS_SHA" ] || { echo "Check out the reviewed operations_commit first" >&2; exit 2; }
git -C "$ROOT" diff --quiet HEAD -- infra/deploy || { echo "Commit and review the operations scripts first" >&2; exit 2; }
identity=$(aws sts get-caller-identity --output json)
[ "$(jq -r .Account <<< "$identity")" = "$ACCOUNT" ] || { echo "Wrong AWS account" >&2; exit 2; }
case $(jq -r .Arn <<< "$identity") in *:assumed-role/*github*|*:root) echo "Use the owner deployment identity, not CI or root" >&2; exit 2 ;; esac
[ "$(aws ec2 describe-instances --instance-ids "$INSTANCE" --query 'Reservations[0].Instances[0].State.Name' --output text)" = running ] || {
  echo "The target is not running. Start it separately; no deployment occurred." >&2; exit 2;
}
# Verify the candidate still has a successful main CI run. This proves recorded
# checks, not owner acceptance or compatibility with a newer database.
passed=$(gh api "repos/harsha135135/One3D/actions/workflows/ci.yml/runs?head_sha=$SHA&branch=main&event=push" \
  --jq '[.workflow_runs[] | select(.conclusion == "success")] | length')
[ "$passed" -ge 1 ] || { echo "No successful main CI run for the release commit" >&2; exit 2; }
for service in backend frontend simulator; do
  expected=$(jq -r --arg service "$service" '.images[$service] | split("@")[1]' "$MANIFEST")
  actual=$(aws ecr describe-images --repository-name "one3d-$service" --image-ids "imageTag=$SHA" \
    --query 'imageDetails[0].imageDigest' --output text)
  [ "$actual" = "$expected" ] || { echo "$service image digest differs from the release manifest" >&2; exit 2; }
done
WORK=$(mktemp -d)
trap 'rm -rf "$WORK"' EXIT
aws s3api get-object --bucket "$BUCKET" --key "$KEY" --version-id "$VERSION" "$WORK/source.tar.gz" > /dev/null
python3 "$HERE/release_manifest.py" "$MANIFEST" --bundle "$WORK/source.tar.gz"
mkdir "$WORK/app"
tar -xzf "$WORK/source.tar.gz" -C "$WORK/app"
# Preserve the candidate's application configuration, but use reviewed release
# operations rather than the historical bundle's auto-deployment script.
cp "$HERE/deploy.sh" "$HERE/release_manifest.py" "$HERE/host-lock.sh" "$WORK/app/infra/deploy/"
cp "$MANIFEST" "$WORK/app/release.json"
python3 "$HERE/release_manifest.py" "$MANIFEST" --compose "$WORK/app/compose.release.json"
tar -czf "$WORK/envelope.tar.gz" -C "$WORK/app" .
HASH=$(python3 -c 'import hashlib,sys; print(hashlib.sha256(open(sys.argv[1],"rb").read()).hexdigest())' "$WORK/envelope.tar.gz")
ENVELOPE="deploy/release-envelopes/$SHA-$HASH.tar.gz"
# This write and the SSM call below are live changes, only after owner approval.
# CI cannot execute the envelope; the host checks its hash before unpacking.
aws s3 cp "$WORK/envelope.tar.gz" "s3://$BUCKET/$ENVELOPE" --only-show-errors
commands=$(jq -n --arg sha "$SHA" --arg bucket "$BUCKET" --arg key "$ENVELOPE" --arg hash "$HASH" --arg region "$REGION" \
  --arg lock "$(cat "$HERE/host-lock.sh")" '[
  "set -eu",
  $lock,
  "one3d_lock /opt/one3d",
  "one3d_release_stage=$(mktemp -d /opt/one3d/release-stage.XXXXXX)",
  "trap '\''rm -rf \"$one3d_release_stage\"'\'' EXIT",
  "aws s3 cp s3://\($bucket)/\($key) \"$one3d_release_stage/envelope.tar.gz\" --region \($region) --only-show-errors",
  "printf '\''%s  %s\\n'\'' \($hash) \"$one3d_release_stage/envelope.tar.gz\" | sha256sum -c -",
  "mkdir \"$one3d_release_stage/app\"",
  "tar -xzf \"$one3d_release_stage/envelope.tar.gz\" -C \"$one3d_release_stage/app\"",
  "test ! -e /opt/one3d/app.previous || { echo Previous deployment bundle exists: archive it before another promotion >&2; exit 1; }",
  "if [ -d /opt/one3d/app ]; then mv /opt/one3d/app /opt/one3d/app.previous; fi",
  "mv \"$one3d_release_stage/app\" /opt/one3d/app",
  "bash /opt/one3d/app/infra/deploy/deploy.sh \($sha)"
]')
id=$(aws ssm send-command --instance-ids "$INSTANCE" --document-name AWS-RunShellScript \
  --comment "Approved One3D release ${SHA:0:12}" --timeout-seconds 600 \
  --parameters "$(jq -n --argjson c "$commands" '{commands: $c, executionTimeout: ["900"]}')" \
  --query Command.CommandId --output text)
echo "SSM command $id"
status=Pending
for i in $(seq 1 120); do
  status=$(aws ssm get-command-invocation --command-id "$id" --instance-id "$INSTANCE" --query Status --output text 2>/dev/null || echo Pending)
  case $status in Pending|InProgress|Delayed) sleep 10 ;; *) break ;; esac
done
aws ssm get-command-invocation --command-id "$id" --instance-id "$INSTANCE" --query '[StandardOutputContent, StandardErrorContent]' --output text
echo "Deploy status: $status"
[ "$status" = Success ] || { echo "Promotion failed. Preserve app.previous; do not restore or downgrade the live database automatically." >&2; exit 1; }
