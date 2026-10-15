# Amazon Linux 2023, x86_64, latest at creation. Later AMI releases do not
# replace the running instance.
data "aws_ssm_parameter" "al2023" {
  name = "/aws/service/ami-amazon-linux-latest/al2023-ami-kernel-default-x86_64"
}

data "aws_subnet" "chosen" {
  id = sort(data.aws_subnets.default.ids)[0]
}

resource "aws_instance" "demo" {
  ami                     = nonsensitive(data.aws_ssm_parameter.al2023.value)
  instance_type           = var.instance_type
  subnet_id               = data.aws_subnet.chosen.id
  disable_api_termination = var.termination_protection
  # Never bill surplus CPU credits: sustained load is throttled to the
  # baseline instead (T instances default to "unlimited").
  credit_specification {
    cpu_credits = "standard"
  }
  vpc_security_group_ids = [aws_security_group.web.id]
  iam_instance_profile   = aws_iam_instance_profile.demo.name

  # IMDSv2 only. Two hops, so the backend container (one network hop further)
  # can use the instance role for S3.
  metadata_options {
    http_tokens                 = "required"
    http_put_response_hop_limit = 2
  }

  root_block_device {
    volume_type           = "gp3"
    volume_size           = var.root_volume_gb
    encrypted             = true
    delete_on_termination = true
  }

  user_data = templatefile("${path.module}/user_data.sh.tftpl", {
    region          = var.region
    compose_version = var.compose_version
    data_device     = "/dev/disk/by-id/nvme-Amazon_Elastic_Block_Store_${replace(aws_ebs_volume.data.id, "-", "")}"
  })

  tags = { Name = local.name }

  # A new AMI release or a changed first-boot script never replaces the
  # running instance (replacement is an explicit decision: terraform apply -replace).
  lifecycle {
    ignore_changes  = [ami, user_data]
    prevent_destroy = true
  }
}

# The data volume lives apart from the instance: stopping, starting or even
# replacing the instance keeps the database, the simulator journal and the
# certificates.
resource "aws_ebs_volume" "data" {
  lifecycle {
    prevent_destroy = true
  }
  availability_zone = data.aws_subnet.chosen.availability_zone
  size              = var.data_volume_gb
  type              = "gp3"
  encrypted         = true
  tags              = { Name = "${local.name}-data" }
}

resource "aws_volume_attachment" "data" {
  device_name = "/dev/sdf"
  volume_id   = aws_ebs_volume.data.id
  instance_id = aws_instance.demo.id
}

resource "aws_eip" "demo" {
  lifecycle {
    prevent_destroy = true
  }
  domain   = "vpc"
  instance = aws_instance.demo.id
  tags     = { Name = local.name }
}

# --- the instance's role ------------------------------------------------------

resource "aws_iam_role" "instance" {
  name = "${local.name}-instance"
  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Principal = { Service = "ec2.amazonaws.com" }
      Action    = "sts:AssumeRole"
    }]
  })
}

resource "aws_iam_role_policy_attachment" "ssm_core" {
  role       = aws_iam_role.instance.name
  policy_arn = "arn:aws:iam::aws:policy/AmazonSSMManagedInstanceCore"
}

resource "aws_iam_role_policy" "instance" {
  name = "${local.name}-app"
  role = aws_iam_role.instance.id
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Sid      = "EcrLogin"
        Effect   = "Allow"
        Action   = "ecr:GetAuthorizationToken"
        Resource = "*"
      },
      {
        Sid      = "PullOwnImages"
        Effect   = "Allow"
        Action   = ["ecr:BatchGetImage", "ecr:GetDownloadUrlForLayer", "ecr:BatchCheckLayerAvailability"]
        Resource = [for repo in aws_ecr_repository.app : repo.arn]
      },
      {
        # The backend signs browser uploads (PUT) and reads exact versions.
        Sid      = "ModelFiles"
        Effect   = "Allow"
        Action   = ["s3:PutObject", "s3:GetObject", "s3:GetObjectVersion"]
        Resource = "${aws_s3_bucket.artifacts.arn}/uploads/*"
      },
      {
        Sid      = "DeployBundles"
        Effect   = "Allow"
        Action   = "s3:GetObject"
        Resource = "${aws_s3_bucket.ops.arn}/deploy/*"
      },
      {
        Sid      = "DatabaseBackups"
        Effect   = "Allow"
        Action   = ["s3:PutObject", "s3:GetObject"]
        Resource = "${aws_s3_bucket.ops.arn}/backups/*"
      },
      {
        Sid    = "AppConfiguration"
        Effect = "Allow"
        Action = ["ssm:GetParametersByPath", "ssm:GetParameters", "ssm:GetParameter"]
        Resource = [
          "arn:aws:ssm:${var.region}:${local.account_id}:parameter${local.params}",
          "arn:aws:ssm:${var.region}:${local.account_id}:parameter${local.params}/*",
        ]
      },
    ]
  })
}

resource "aws_iam_instance_profile" "demo" {
  name = "${local.name}-instance"
  role = aws_iam_role.instance.name
}
