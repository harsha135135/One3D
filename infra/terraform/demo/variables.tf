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
  description = "Encrypted gp3 root volume: OS, Docker images, the database and the simulator journal."
  type        = number
  default     = 20
}

variable "github_repository" {
  description = "owner/name of the repository whose main branch may push images and deploy."
  type        = string
  default     = "harsha135135/One3D"
}

variable "compose_version" {
  description = "Docker Compose plugin release installed on the instance."
  type        = string
  default     = "v5.6.0"
}
