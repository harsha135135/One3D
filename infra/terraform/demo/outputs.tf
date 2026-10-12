output "public_ip" {
  description = "Elastic IP: the A record for the domain points here."
  value       = aws_eip.demo.public_ip
}

output "instance_id" {
  value = aws_instance.demo.id
}

output "data_volume_id" {
  value = aws_ebs_volume.data.id
}

output "github_deploy_role_arn" {
  value = aws_iam_role.github_deploy.arn
}

output "ecr_registry" {
  value = local.plain["one3d-registry"]
}

output "artifacts_bucket" {
  value = aws_s3_bucket.artifacts.bucket
}

output "ops_bucket" {
  value = aws_s3_bucket.ops.bucket
}

output "cognito_user_pool_id" {
  value = aws_cognito_user_pool.demo.id
}

output "cognito_sign_in_domain" {
  value = local.cognito_domain
}
