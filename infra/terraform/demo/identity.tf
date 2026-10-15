# Sign-in for the demo. MFA (TOTP authenticator app) is a pool-level setting,
# so it is REQUIRED for everyone in this pool: operators must use it, and the
# synthetic demo customers do too. Separate pools per role would be a V2 option.
resource "aws_cognito_user_pool" "demo" {
  name              = local.name
  user_pool_tier    = "LITE"
  mfa_configuration = "ON"

  software_token_mfa_configuration {
    enabled = true
  }

  # No self sign-up: demo users are created by an administrator.
  admin_create_user_config {
    allow_admin_create_user_only = true
  }

  username_attributes      = ["email"]
  auto_verified_attributes = ["email"]

  password_policy {
    minimum_length                   = 14
    require_lowercase                = true
    require_uppercase                = true
    require_numbers                  = true
    require_symbols                  = false
    temporary_password_validity_days = 3
  }

  account_recovery_setting {
    recovery_mechanism {
      name     = "admin_only"
      priority = 1
    }
  }

  deletion_protection = "ACTIVE"
  lifecycle {
    prevent_destroy = true
  }
}

# Members get the OPERATOR role in the application (cognito:groups claim).
resource "aws_cognito_user_group" "operators" {
  name         = "operators"
  user_pool_id = aws_cognito_user_pool.demo.id
  description  = "One3D operators"
}

# The hosted sign-in pages (classic hosted UI). They also enrol the
# authenticator app the first time a user signs in.
resource "aws_cognito_user_pool_domain" "demo" {
  domain                = "${local.name}-${random_id.suffix.hex}"
  user_pool_id          = aws_cognito_user_pool.demo.id
  managed_login_version = 1
}

# The backend is a confidential OIDC client (authorization code flow).
# ALLOW_ADMIN_USER_PASSWORD_AUTH lets an administrator with IAM credentials
# enrol the synthetic demo users' authenticators from a script.
resource "aws_cognito_user_pool_client" "web" {
  name                                 = "one3d-web"
  user_pool_id                         = aws_cognito_user_pool.demo.id
  generate_secret                      = true
  allowed_oauth_flows_user_pool_client = true
  allowed_oauth_flows                  = ["code"]
  allowed_oauth_scopes                 = ["openid", "email", "profile"]
  supported_identity_providers         = ["COGNITO"]
  callback_urls                        = ["https://${var.domain}/login/oauth2/code/oidc"]
  logout_urls                          = ["https://${var.domain}/"]
  explicit_auth_flows                  = ["ALLOW_USER_SRP_AUTH", "ALLOW_ADMIN_USER_PASSWORD_AUTH", "ALLOW_REFRESH_TOKEN_AUTH"]
  prevent_user_existence_errors        = "ENABLED"
  enable_token_revocation              = true
  id_token_validity                    = 60
  access_token_validity                = 60
  refresh_token_validity               = 1
  token_validity_units {
    id_token      = "minutes"
    access_token  = "minutes"
    refresh_token = "days"
  }
}
