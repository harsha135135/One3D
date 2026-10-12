terraform {
  required_version = ">= 1.10"
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 6.68"
    }
    random = {
      source  = "hashicorp/random"
      version = "~> 3.9"
    }
  }
  # State in the bootstrap bucket; the bucket name is account-specific:
  #   terraform init -backend-config="bucket=one3d-tfstate-<account id>"
  backend "s3" {
    key          = "demo/terraform.tfstate"
    region       = "ap-south-1"
    encrypt      = true
    use_lockfile = true
  }
}

provider "aws" {
  region = var.region
  default_tags {
    tags = { Project = "one3d", Environment = "demo", ManagedBy = "terraform" }
  }
}
