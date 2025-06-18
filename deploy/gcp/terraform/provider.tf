# Terraform Google Cloud Platform (GCP) Provider Configuration

terraform {
  required_providers {
    google = {
      source  = "hashicorp/google"
      version = "~> 5.10" # Specify a compatible version range for the Google provider
    }
    random = { # For generating unique names or suffixes if needed
      source  = "hashicorp/random"
      version = "~> 3.5"
    }
    # Add other providers like google-beta if specific beta features are used.
    # google-beta = {
    #   source  = "hashicorp/google-beta"
    #   version = "~> 5.10"
    # }
  }
}

# Variables used by the provider block (defined in variables.tf)
variable "gcp_project_id" {
  description = "The GCP project ID to deploy resources into."
  type        = string
  # No default, must be provided.
}

variable "gcp_region" {
  description = "The GCP region for deploying regional resources."
  type        = string
  # default     = "us-central1" # Example default
}

# Google Cloud Provider Block
# Authentication:
# The Google provider supports several ways to authenticate:
# 1. Google Cloud CLI (gcloud): If you are logged in via `gcloud auth application-default login`, Terraform uses these credentials. (Common for local dev)
# 2. Service Account Key File: Set the GOOGLE_APPLICATION_CREDENTIALS environment variable to the path of your JSON key file. (Common for CI/CD)
# 3. Impersonating a Service Account: If the primary credentials have `roles/iam.serviceAccountTokenCreator`.
# 4. Metadata Concealment: For GCE instances, Cloud Run, etc., using the attached service account.
provider "google" {
  project = var.gcp_project_id
  region  = var.gcp_region
  # zone    = var.gcp_zone # Optional: can be set at resource level or if all resources are zonal

  # Optional: User project override for billing purposes if different from the project where resources are created.
  # user_project_override = true
  # billing_project       = "YOUR_BILLING_PROJECT_ID"

  # Optional: Impersonate a service account
  # impersonate_service_account = "target-sa@${var.gcp_project_id}.iam.gserviceaccount.com"
}

# --- Optional: Google Beta Provider ---
# Some GCP resources or features might only be available in the google-beta provider.
# If you need to use beta features, uncomment and configure this provider.
# You would then use `provider = google-beta` in the specific resource blocks.
/*
provider "google-beta" {
  project = var.gcp_project_id
  region  = var.gcp_region
  # zone    = var.gcp_zone

  # (Authentication configuration similar to the 'google' provider)
}
*/

# GCP provider does not have a direct `default_tags` or `default_labels` block like AWS.
# Labels are applied per resource. Common labels can be managed using a `locals` block
# and merged into each resource's `labels` argument.
# We will apply labels directly in each resource definition.
# Variables like project_name, environment_name for labeling are defined in variables.tf.
