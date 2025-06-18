# --- Variables ---
variable "gcp_project_id" {
  description = "The GCP project ID where the Artifact Registry will be created."
  type        = string
}

variable "gcp_region" {
  description = "The GCP region for the Artifact Registry repository."
  type        = string
}

variable "project_name" { # General project name for context if needed, not directly used in AR repo name usually
  description = "Name of the project."
  type        = string
}

variable "artifact_registry_repository_name" {
  description = "The name for the Artifact Registry repository (e.g., 'atomic-docker-repo'). Needs to be unique within the project and region."
  type        = string
  # default     = "atomic-app-images" # Example, can be derived from project_name
}

variable "gke_node_service_account_email_input" {
  description = "The email of the GCP Service Account used by GKE nodes. This SA needs pull access to the Artifact Registry. Should be prefixed with 'serviceAccount:' if it's the full member string, or just email if provider handles prefix."
  type        = string
  # Example: from output of iam_service_accounts.tf or "default" for Compute Engine default SA.
  # For iam_member, it usually expects the full "serviceAccount:email" or "user:email" etc.
  # However, for some resources, just email is enough if type is known.
  # Let's assume this variable will receive the email address directly.
}

variable "cicd_service_account_email_gcp_input" {
  description = "Optional: The email of the GCP Service Account used by CI/CD systems for pushing images. If empty, no writer role is assigned by this module. Should be prefixed with 'serviceAccount:' or just email."
  type        = string
  default     = ""
}

# --- Google Artifact Registry Repository for Docker Images ---
resource "google_artifact_registry_repository" "main" {
  project       = var.gcp_project_id
  location      = var.gcp_region # Artifact Registry repositories are regional
  repository_id = var.artifact_registry_repository_name # This is the user-defined name for the repo
  description   = "Docker repository for ${var.project_name} applications"
  format        = "DOCKER" # Specify repository format

  # Optional: KMS key for encryption
  # kms_key_name = "projects/PROJECT_ID/locations/REGION/keyRings/KEYRING_NAME/cryptoKeys/KEY_NAME"

  # Optional: Labels
  # labels = {
  #   environment = "production" # Or var.environment_name
  #   project     = var.project_name
  # }

  # Optional: Immutable tags (recommended for production images)
  # lifecycle_policy {
  #   rules = [
  #     {
  #       action = {
  #         type = "IMMUTABLE_TAGS"
  #       }
  #       condition = {
  #         tag_prefixes = ["prod-"] # Example: make tags starting with "prod-" immutable
  #       }
  #     }
  #   ]
  # }
}

# --- IAM Permissions for the Artifact Registry Repository ---

# Grant GKE Node Service Account permissions to pull images (Reader role)
resource "google_artifact_registry_repository_iam_member" "gke_node_sa_ar_reader" {
  project    = google_artifact_registry_repository.main.project
  location   = google_artifact_registry_repository.main.location
  repository = google_artifact_registry_repository.main.name # Use .name which is same as repository_id for AR
  role       = "roles/artifactregistry.reader"
  member     = "serviceAccount:${var.gke_node_service_account_email_input}" # Ensure email is passed correctly

  # Condition can be added if needed
}

# Grant CI/CD Service Account permissions to push images (Writer role) - Conditionally
resource "google_artifact_registry_repository_iam_member" "cicd_sa_ar_writer" {
  count = var.cicd_service_account_email_gcp_input != "" ? 1 : 0

  project    = google_artifact_registry_repository.main.project
  location   = google_artifact_registry_repository.main.location
  repository = google_artifact_registry_repository.main.name
  role       = "roles/artifactregistry.writer"
  member     = "serviceAccount:${var.cicd_service_account_email_gcp_input}"
}


# --- Outputs (typically in outputs.tf) ---
# Moved to outputs.tf as per best practice.
# output "artifact_registry_repository_id" {
#   description = "The ID of the Artifact Registry repository."
#   value       = google_artifact_registry_repository.main.id # Format: projects/<project>/locations/<location>/repositories/<repositoryId>
# }
#
# output "artifact_registry_repository_name_output" { # Renamed to avoid conflict with var
#   description = "The name (repository_id) of the Artifact Registry repository."
#   value       = google_artifact_registry_repository.main.repository_id
# }
#
# output "artifact_registry_repository_url" {
#   description = "The URL of the Artifact Registry repository (e.g., <location>-docker.pkg.dev/<project>/<repository>/<image>)."
#   # The full URL for Docker is typically <location>-docker.pkg.dev/<project_id>/<repository_id>
#   # This can be constructed or might be part of an attribute in future provider versions.
#   # For now, constructing it based on known pattern:
#   value       = "${var.gcp_region}-docker.pkg.dev/${var.gcp_project_id}/${google_artifact_registry_repository.main.repository_id}"
# }
#
# output "artifact_registry_repository_format" {
#   description = "The format of the Artifact Registry repository."
#   value       = google_artifact_registry_repository.main.format
# }
