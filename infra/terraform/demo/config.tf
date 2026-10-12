# Configuration for the backend, read by deploy.sh on the instance.
# The database password, the OIDC client secret and Grafana's admin password
# are generated here and are therefore in the (private, encrypted) Terraform
# state; Razorpay's keys are put in by hand with the AWS CLI and never are.

resource "random_password" "db" {
  length  = 32
  special = false
}

resource "random_password" "grafana_admin" {
  length  = 32
  special = false
}

locals {
  cognito_issuer = "https://cognito-idp.${var.region}.amazonaws.com/${aws_cognito_user_pool.demo.id}"
  cognito_domain = "https://${aws_cognito_user_pool_domain.demo.domain}.auth.${var.region}.amazoncognito.com"
  plain = {
    "one3d-registry"               = "${local.account_id}.dkr.ecr.${var.region}.amazonaws.com"
    "one3d-domain"                 = var.domain
    "one3d-public-url"             = "https://${var.domain}"
    "one3d-upload-origin"          = "https://${aws_s3_bucket.artifacts.bucket}.s3.${var.region}.amazonaws.com"
    "one3d-storage-bucket"         = aws_s3_bucket.artifacts.bucket
    "one3d-storage-region"         = var.region
    "one3d-oidc-issuer"            = local.cognito_issuer
    "one3d-oidc-jwk-set-uri"       = "${local.cognito_issuer}/.well-known/jwks.json"
    "one3d-oidc-authorization-uri" = "${local.cognito_domain}/oauth2/authorize"
    "one3d-oidc-token-uri"         = "${local.cognito_domain}/oauth2/token"
    "one3d-oidc-user-info-uri"     = "${local.cognito_domain}/oauth2/userInfo"
    "one3d-oidc-client-id"         = aws_cognito_user_pool_client.web.id
    "one3d-razorpay-enabled"       = "true"
  }
}

resource "aws_ssm_parameter" "plain" {
  for_each = local.plain
  name     = "${local.params}/${each.key}"
  type     = "String"
  value    = each.value
}

resource "aws_ssm_parameter" "db_password" {
  name  = "${local.params}/one3d-db-password"
  type  = "SecureString"
  value = random_password.db.result
}

# Grafana is reached only through SSM port forwarding; its admin still needs a password.
resource "aws_ssm_parameter" "grafana_admin_password" {
  name  = "${local.params}/one3d-grafana-admin-password"
  type  = "SecureString"
  value = random_password.grafana_admin.result
}

resource "aws_ssm_parameter" "oidc_client_secret" {
  name  = "${local.params}/one3d-oidc-client-secret"
  type  = "SecureString"
  value = aws_cognito_user_pool_client.web.client_secret
}
