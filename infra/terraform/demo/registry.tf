# Image repositories. Tags are immutable: a commit SHA always names the same
# image, so what CI tested is what runs.
resource "aws_ecr_repository" "app" {
  for_each             = toset(["one3d-backend", "one3d-frontend", "one3d-simulator"])
  name                 = each.key
  image_tag_mutability = "IMMUTABLE"
  force_delete         = false

  lifecycle {
    prevent_destroy = true
  }

  image_scanning_configuration {
    scan_on_push = true
  }

  encryption_configuration {
    encryption_type = "AES256"
  }
}

resource "aws_ecr_lifecycle_policy" "app" {
  for_each   = aws_ecr_repository.app
  repository = each.value.name
  policy = jsonencode({
    rules = [{
      rulePriority = 1
      description  = "Expire only untagged images; retain every commit and release tag"
      selection = {
        tagStatus   = "untagged"
        countType   = "sinceImagePushed"
        countUnit   = "days"
        countNumber = 7
      }
      action = { type = "expire" }
    }]
  })
}
