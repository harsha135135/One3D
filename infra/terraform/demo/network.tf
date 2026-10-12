data "aws_vpc" "default" {
  default = true
}

data "aws_subnets" "default" {
  filter {
    name   = "vpc-id"
    values = [data.aws_vpc.default.id]
  }
  filter {
    name   = "default-for-az"
    values = ["true"]
  }
}

# Only HTTP (redirect and certificate challenge) and HTTPS are open. There is
# no SSH: administration uses SSM Session Manager and Run Command.
resource "aws_security_group" "web" {
  name        = "${local.name}-web"
  description = "One3D demo: HTTP and HTTPS only; no SSH"
  vpc_id      = data.aws_vpc.default.id
}

resource "aws_vpc_security_group_ingress_rule" "http" {
  security_group_id = aws_security_group.web.id
  description       = "HTTP (redirects to HTTPS; Let's Encrypt challenge)"
  ip_protocol       = "tcp"
  from_port         = 80
  to_port           = 80
  cidr_ipv4         = "0.0.0.0/0"
}

resource "aws_vpc_security_group_ingress_rule" "https" {
  security_group_id = aws_security_group.web.id
  description       = "HTTPS"
  ip_protocol       = "tcp"
  from_port         = 443
  to_port           = 443
  cidr_ipv4         = "0.0.0.0/0"
}

resource "aws_vpc_security_group_egress_rule" "all" {
  security_group_id = aws_security_group.web.id
  description       = "Outbound: ECR, S3, SSM, Cognito, Razorpay, Let's Encrypt, package updates"
  ip_protocol       = "-1"
  cidr_ipv4         = "0.0.0.0/0"
}
