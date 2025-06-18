# Variables to be defined in variables.tf or passed as input
variable "gcp_project_id" {
  description = "The GCP project ID where resources will be deployed."
  type        = string
}

variable "gcp_region" {
  description = "The GCP region, used for provider configuration and some regional resources (Secret Manager secrets can be global or regional)."
  type        = string
}

variable "project_name" { # General project name for labeling, distinct from gcp_project_id if needed
  description = "The common name of the project, used for tagging and naming conventions."
  type        = string
}

variable "secrets_to_create_in_gcp_sm" {
  description = "A map of secrets to create in Google Secret Manager. Key is the secret_id, value is a description."
  type        = map(string)
  default = {
    # Database Secrets
    "POSTGRES_USER"                = "Username for Cloud SQL for PostgreSQL."
    "POSTGRES_PASSWORD"            = "Password for Cloud SQL for PostgreSQL."

    # Hasura Secrets
    "HASURA_GRAPHQL_ADMIN_SECRET"  = "Admin secret for Hasura GraphQL engine."
    "HASURA_GRAPHQL_JWT_SECRET"    = "JWT secret for Hasura GraphQL engine (JSON format)." # Stored as a string

    # MinIO/Storage Secrets (if using GCS with HMAC keys or custom MinIO, these names are generic)
    "STORAGE_ACCESS_KEY"           = "Access key for Google Cloud Storage HMAC or MinIO."
    "STORAGE_SECRET_KEY"           = "Secret key for Google Cloud Storage HMAC or MinIO."

    # Functions Service Secrets (examples)
    "OPENAI_API_KEY"               = "API key for OpenAI services."
    "BASIC_AUTH_FUNCTIONS_ADMIN"   = "Basic authentication credentials for -admin endpoints in functions service (e.g., user:pass)."
    "GOOGLE_CLIENT_ID_ATOMIC_WEB"  = "Google Client ID for Atomic Web application (used by Functions)." # This is a Google OAuth Client ID
    "ZOOM_CLIENT_SECRET"           = "Zoom Client Secret (used by Functions)."

    # Other secrets based on previous lists, adapting names if needed (underscores are common for GCP SM secret_ids from TF)
    "TRAEFIK_USER"                     = "Username for Traefik dashboard basic authentication."
    "TRAEFIK_PASSWORD"                 = "Password for Traefik dashboard basic authentication."
    "GOOGLE_CLIENT_ID_ANDROID"         = "Google Client ID for Android application."
    "GOOGLE_CLIENT_ID_IOS"             = "Google Client ID for iOS application."
    "GOOGLE_CLIENT_SECRET_ATOMIC_WEB"  = "Google Client Secret for Atomic Web application."
    "GOOGLE_CLIENT_SECRET_WEB"         = "Google Client Secret for Web application."
    "KAFKA_USERNAME"                   = "Username for Kafka SASL authentication (if enabled)."
    "KAFKA_PASSWORD"                   = "Password for Kafka SASL authentication (if enabled)."
    "OPENSEARCH_USERNAME"              = "Username for OpenSearch security (if enabled)."
    "OPENSEARCH_PASSWORD"              = "Password for OpenSearch security (if enabled)."
    "ZOOM_PASS_KEY"                    = "Zoom Pass Key for encryption."
    "ZOOM_CLIENT_ID"                   = "Zoom Client ID."
    "ZOOM_SALT_FOR_PASS"               = "Zoom Salt for Pass Key."
    "ZOOM_IV_FOR_PASS"                 = "Zoom IV for Pass Key."
    "ZOOM_WEBHOOK_SECRET_TOKEN"        = "Zoom Webhook Secret Token."
    "API_TOKEN"                        = "General API Token for custom services (e.g., Optaplanner)."
  }
}

variable "gke_secret_accessor_gsa_email" {
  description = "The email address of the GCP Service Account that Kubernetes Service Accounts will impersonate (via Workload Identity) to access secrets. Must be in the format 'serviceAccount:your-gsa-email@your-gcp-project.iam.gserviceaccount.com'."
  type        = string
  # Example: "serviceAccount:gke-secrets-accessor@${var.gcp_project_id}.iam.gserviceaccount.com"
}

# --- Google Secret Manager Secrets (Placeholders) ---
resource "google_secret_manager_secret" "app_secrets" {
  for_each = var.secrets_to_create_in_gcp_sm

  project   = var.gcp_project_id
  secret_id = each.key # Secret ID (name) in Secret Manager

  replication {
    automatic = true # Or configure user_managed replication if needed
  }

  labels = {
    description = substr(each.value, 0, 63) # Use map value as description, truncated if too long for label
    project     = var.project_name
    managed-by  = "terraform"
  }
}

# --- Initial Versions for Google Secret Manager Secrets ---
resource "google_secret_manager_secret_version" "app_secrets_initial_version" {
  for_each = google_secret_manager_secret.app_secrets

  secret      = each.value.id # Full ID of the secret resource
  secret_data = "TO-BE-SET-MANUALLY-OR-VIA-CICD" # Placeholder data

  # This lifecycle block ensures that Terraform does not try to revert changes
  # made to the secret data outside of Terraform after initial creation.
  lifecycle {
    ignore_changes = [secret_data]
  }
}

# --- IAM Bindings for GKE Service Account to access Secrets ---
# Grant the specified GSA the 'roles/secretmanager.secretAccessor' role for each secret.
# This GSA will be linked to K8s SAs via Workload Identity.
resource "google_secret_manager_secret_iam_member" "gke_secret_accessor_binding" {
  for_each = google_secret_manager_secret.app_secrets

  project   = each.value.project
  secret_id = each.value.secret_id
  role      = "roles/secretmanager.secretAccessor"
  member    = var.gke_secret_accessor_gsa_email # e.g., "serviceAccount:my-gsa@my-project.iam.gserviceaccount.com"
}

# Outputs
output "secret_manager_secret_ids_map" {
  description = "A map of the logical secret name to its full Google Secret Manager ID."
  value       = { for k, v in google_secret_manager_secret.app_secrets : k => v.id }
}

output "secret_manager_secret_names_map" {
  description = "A map of the logical secret name to its short Secret ID (name) in Secret Manager."
  value       = { for k, v in google_secret_manager_secret.app_secrets : k => v.secret_id }
}
