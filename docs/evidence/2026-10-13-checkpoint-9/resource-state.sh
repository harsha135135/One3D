#!/bin/bash
# Checkpoint 9: the demo's AWS resources at one moment (read-only), to compare before and after testing.
export AWS_PROFILE=${AWS_PROFILE:-one3d-deployer} AWS_REGION=ap-south-1 AWS_PAGER=""
ROOT=$(cd "$(dirname "$0")/../../.." && pwd)
tf() { terraform -chdir="$ROOT/infra/terraform/demo" output -raw "$1"; }
I=$(tf instance_id)
echo "# $(date -u +%FT%TZ)"
echo "instance $I: $(aws ec2 describe-instances --instance-ids "$I" --query 'Reservations[0].Instances[0].[State.Name,InstanceType]' --output text | tr '\t' ' '), termination protection $(aws ec2 describe-instance-attribute --instance-id "$I" --attribute disableApiTermination --query DisableApiTermination.Value --output text)"
echo "data volume: $(aws ec2 describe-volumes --volume-ids "$(tf data_volume_id)" --query 'Volumes[0].[VolumeId,Size,State,Attachments[0].InstanceId,Encrypted]' --output text | tr '\t' ' ')"
echo "Elastic IP: $(aws ec2 describe-addresses --query 'Addresses[].[PublicIp,InstanceId]' --output text | tr '\t' ' ')"
echo "unattached volumes: $(aws ec2 describe-volumes --filters Name=status,Values=available --query 'length(Volumes)' --output text); snapshots: $(aws ec2 describe-snapshots --owner-ids self --query 'length(Snapshots)' --output text)"
for b in "$(tf artifacts_bucket)" "$(tf ops_bucket)"; do
  echo "bucket ${b%-*}-…: versioning $(aws s3api get-bucket-versioning --bucket "$b" --query Status --output text), objects $(aws s3api list-objects-v2 --bucket "$b" --query 'length(Contents)' --output text)"
done
echo "ECR images (backend tags): $(aws ecr describe-images --repository-name one3d-backend --query 'length(imageDetails)' --output text)"
echo "Cognito users: $(aws cognito-idp list-users --user-pool-id "$(tf cognito_user_pool_id)" --query 'length(Users)' --output text)"
echo "site: HTTP $(curl -s -o /dev/null -w '%{http_code}' --max-time 10 https://demo.one3d.store/api/catalog/configurations)"
