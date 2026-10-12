data "aws_caller_identity" "current" {}

locals {
  name       = "one3d-demo"
  account_id = data.aws_caller_identity.current.account_id
  # Parameters rendered into the backend's environment by deploy.sh: the
  # parameter's last path segment becomes the variable name (one3d-domain ->
  # ONE3D_DOMAIN). Razorpay's three keys are added with the AWS CLI, not
  # Terraform, so they never enter the Terraform state.
  params = "/one3d/demo"
}

# Random suffix for globally unique names (bucket names, Cognito domain).
resource "random_id" "suffix" {
  byte_length = 3
}
