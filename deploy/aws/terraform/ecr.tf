# --- Variables ---
variable "project_name" {
  description = "Name of the project, used for prefixing ECR repository names and tagging."
  type        = string
}

variable "custom_image_names" {
  description = "A list of custom Docker image names for which ECR repositories will be created (e.g., [\"atomic-functions\", \"atomic-app\"])."
  type        = list(string)
  default = [
    "atomic-scheduler", # For Optaplanner
    "atomic-functions", # For Functions service
    "atomic-handshake", # For Handshake service
    "atomic-oauth",     # For OAuth service
    "atomic-app"        # For App (frontend) service
    # Add any other custom images that need an ECR repository
  ]
}

variable "ecr_image_tag_mutability" {
  description = "The image tag mutability setting for the ECR repositories (MUTABLE or IMMUTABLE)."
  type        = string
  default     = "MUTABLE" # Or "IMMUTABLE" for stricter version control
  validation {
    condition     = contains(["MUTABLE", "IMMUTABLE"], var.ecr_image_tag_mutability)
    error_message = "Image tag mutability must be either MUTABLE or IMMUTABLE."
  }
}

variable "ecr_scan_on_push" {
  description = "Whether to enable image scanning on push for ECR repositories."
  type        = bool
  default     = true
}

# --- ECR Repositories ---
resource "aws_ecr_repository" "main" {
  for_each = toset(var.custom_image_names) # Use toset to ensure unique names if list has duplicates

  name                 = "${var.project_name}-${each.key}" # Repository name, e.g., "myproject-atomic-functions"
  image_tag_mutability = var.ecr_image_tag_mutability

  image_scanning_configuration {
    scan_on_push = var.ecr_scan_on_push
  }

  # Optional: Encryption configuration (KMS)
  # encryption_configuration {
  #   encryption_type = "KMS"
  #   kms_key         = aws_kms_key.ecr_kms.arn # If using a custom KMS key
  # }

  # Optional: Lifecycle policy to manage old images
  # lifecycle_policy = jsonencode({
  #   rules = [
  #     {
  #       rulePriority = 1,
  #       description  = "Keep only last 10 images",
  #       selection    = {
  #         tagStatus   = "any",
  #         countType   = "imageCountMoreThan",
  #         countNumber = 10
  #       },
  #       action       = {
  #         type = "expire"
  #       }
  #     }
  #   ]
  # })

  tags = {
    Name      = "${var.project_name}-${each.key}"
    Project   = var.project_name
    Terraform = "true"
  }
}

# --- Outputs (typically in outputs.tf, but defined here for clarity of what this module provides) ---
# Moved to outputs.tf as per best practice.
# output "ecr_repository_urls" {
#   description = "Map of ECR repository names to their URLs."
#   value = {
#     for name, repo in aws_ecr_repository.main :
#     name => repo.repository_url
#   }
# }
#
# output "ecr_repository_arns" {
#   description = "Map of ECR repository names to their ARNs."
#   value = {
#     for name, repo in aws_ecr_repository.main :
#     name => repo.arn
#   }
# }
