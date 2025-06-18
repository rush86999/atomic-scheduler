# Terraform AWS Provider Configuration

# This block configures settings for Terraform itself, including required providers.
terraform {
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0" # Specify a compatible version range for the AWS provider
    }
  }
}

# Variable for AWS region (should be defined in variables.tf or passed as input)
variable "aws_region" {
  description = "The AWS region where resources will be created."
  type        = string
  # default     = "us-east-1" # Or your preferred default region
}

# Variable for Project Name (used in default_tags)
variable "project_name" {
  description = "The name of the project, used for consistent tagging."
  type        = string
  # default     = "atomic-project"
}

# Variable for Environment Name (used in default_tags)
variable "environment_name" {
  description = "The name of the environment (e.g., dev, staging, prod), used for consistent tagging."
  type        = string
  # default     = "development"
}

# AWS Provider Block
provider "aws" {
  region = var.aws_region

  # Default tags to apply to all taggable resources created by this configuration.
  # This helps in organizing and managing resources consistently.
  default_tags {
    tags = {
      Project     = var.project_name
      Environment = var.environment_name
      Terraform   = "true" # Indicates the resource is managed by Terraform
      # Add any other common tags relevant to your organization
      # Example: CostCenter = "12345"
      # Example: Owner      = "team-alpha@example.com"
    }
  }

  # --- Optional: AWS CLI Named Profile ---
  # If you are using AWS CLI named profiles for authentication, uncomment and specify your profile.
  # profile = "your-aws-profile-name"

  # --- Optional: Assume Role Configuration ---
  # If Terraform needs to assume an IAM role for deploying resources, configure it here.
  # This is common in CI/CD environments or when adhering to least privilege principles.
  # assume_role {
  #   role_arn     = "arn:aws:iam::ACCOUNT_ID:role/TerraformExecutionRole" # ARN of the role to assume
  #   session_name = "TerraformSession-${var.project_name}-${var.environment_name}" # Descriptive session name
  #   # external_id  = "YourOptionalExternalID" # If the trust policy of the role requires an external ID
  # }
}
