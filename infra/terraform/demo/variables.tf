variable "region" {
  description = "AWS region of the demo."
  type        = string
  default     = "ap-south-1"
}

variable "domain" {
  description = "Public host name of the demo (an A record at the DNS provider points it at the Elastic IP)."
  type        = string
  default     = "demo.one3d.store"
}

variable "instance_type" {
  description = "x86 instance (images are built on GitHub's x86 runners). The measured budget fits a t3.small; see the Checkpoint 7 evidence."
  type        = string
  default     = "t3.small"
}

variable "root_volume_gb" {
  description = "Encrypted gp3 root volume: the OS and Docker images. The AMI needs at least 8 GB."
  type        = number
  default     = 12
}

variable "data_volume_gb" {
  description = "Separate encrypted gp3 volume for the database, the simulator journal and the TLS certificates; it survives stop/start and instance replacement."
  type        = number
  default     = 4
}

variable "termination_protection" {
  description = "Blocks accidental termination of the instance. Set to false (and apply) only to destroy the demo."
  type        = bool
  default     = true
}

variable "github_oidc_subject_prefix" {
  description = <<-EOT
    Subject prefix of GitHub's OIDC tokens for the repository whose main branch
    may push images and deploy. This repository uses GitHub's immutable subject
    format, owner@owner-id/repository@repository-id, so a renamed or recreated
    repository with the same name cannot match. Read it with:
    gh api repos/<owner>/<repo>/actions/oidc/customization/sub (sub_claim_prefix).
  EOT
  type        = string
  default     = "repo:harsha135135@198421406/One3D@1392957710"
}

variable "compose_version" {
  description = "Docker Compose plugin release installed on the instance."
  type        = string
  default     = "v5.6.0"
}
