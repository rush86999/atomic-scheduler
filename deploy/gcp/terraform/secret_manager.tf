# --- Variables ---
variable "gcp_project_id" {
  description = "The GCP project ID where Secret Manager secrets will be created."
  type        = string
}

variable "gcp_region" {
  description = "The GCP region, used for provider configuration. Secret Manager secrets can be global or regional; replication policy handles this."
  type        = string
}

variable "project_name" { # General project name for labeling
  description = "The common name of the project, used for tagging and labeling conventions."
  type        = string
}

variable "environment_name" { # For labeling
  description = "The deployment environment (e.g., dev, staging, prod)."
  type        = string
}

variable "secrets_to_create_in_gcp_sm" {
  description = "A map of secrets to create in Google Secret Manager. Key is the secret_id (use underscores), value is a description."
  type        = map(string)
  default = {
    # Database Secrets
    "POSTGRES_USER"                = "Username for Cloud SQL for PostgreSQL."
    "POSTGRES_PASSWORD"            = "Password for Cloud SQL for PostgreSQL."

    # Hasura Secrets
    "HASURA_GRAPHQL_ADMIN_SECRET"  = "Admin secret for Hasura GraphQL engine."
    "HASURA_GRAPHQL_JWT_SECRET"    = "JWT secret for Hasura GraphQL engine (JSON format)."

    # Traefik Basic Auth
    "TRAEFIK_USER"                 = "Username for Traefik dashboard basic authentication."
    "TRAEFIK_PASSWORD"             = "Password for Traefik dashboard basic authentication."

    # Functions Service Basic Auth
    "BASIC_AUTH_FUNCTIONS_ADMIN"   = "Basic authentication credentials for -admin endpoints in functions service."

    # API Keys
    "OPENAI_API_KEY"               = "API key for OpenAI services."
    "API_TOKEN"                    = "General API Token for custom services (e.g., Optaplanner)."

    # Google OAuth & Service Credentials (these are for the app's use, not GCP service accounts)
    "GOOGLE_CLIENT_ID_ANDROID"         = "Google OAuth Client ID for Android application."
    "GOOGLE_CLIENT_ID_IOS"             = "Google OAuth Client ID for iOS application."
    "GOOGLE_CLIENT_ID_WEB"             = "Google OAuth Client ID for Web application."
    "GOOGLE_CLIENT_ID_ATOMIC_WEB"      = "Google OAuth Client ID for Atomic Web application."
    "GOOGLE_CLIENT_SECRET_ATOMIC_WEB"  = "Google OAuth Client Secret for Atomic Web application."
    "GOOGLE_CLIENT_SECRET_WEB"         = "Google OAuth Client Secret for Web application."
    "GOOGLE_CALENDAR_ID"               = "Google Calendar ID for integration."
    "GOOGLE_CALENDAR_CREDENTIALS"      = "Google Calendar service account credentials JSON string (for app use)."
    "GOOGLE_MAP_KEY"                   = "Google Maps API Key."
    "GOOGLE_PLACE_API_KEY"             = "Google Places API Key."

    # Storage (MinIO running in K8s or GCS HMAC keys)
    "STORAGE_ACCESS_KEY"           = "Access key for MinIO or GCS HMAC."
    "STORAGE_SECRET_KEY"           = "Secret key for MinIO or GCS HMAC."
    "STORAGE_REGION"               = "Default region for S3 compatible storage (e.g., us-east1 if GCS)."

    # Kafka Credentials
    "KAFKA_USERNAME"               = "Username for Kafka SASL authentication."
    "KAFKA_PASSWORD"               = "Password for Kafka SASL authentication."

    # OpenSearch Credentials
    "OPENSEARCH_USERNAME"          = "Username for OpenSearch security."
    "OPENSEARCH_PASSWORD"          = "Password for OpenSearch security."

    # Zoom Integration Secrets
    "ZOOM_CLIENT_ID"               = "Zoom Client ID."
    "ZOOM_CLIENT_SECRET"           = "Zoom Client Secret."
    "ZOOM_PASS_KEY"                = "Zoom Pass Key for encryption."
    "ZOOM_SALT_FOR_PASS"           = "Zoom Salt for Pass Key."
    "ZOOM_IV_FOR_PASS"             = "Zoom IV for Pass Key."
    "ZOOM_WEBHOOK_SECRET_TOKEN"    = "Zoom Webhook Secret Token."

    # Optaplanner Credentials
    "OPTAPLANNER_USERNAME"         = "Username for Optaplanner service."
    "OPTAPLANNER_PASSWORD"         = "Password for Optaplanner service (often same as API_TOKEN)."

    # SMTP / Email Service Credentials
    "SMTP_HOST"                    = "SMTP server host."
    "SMTP_PORT"                    = "SMTP server port."
    "SMTP_USER"                    = "SMTP username."
    "SMTP_PASS"                    = "SMTP password."
    "SMTP_FROM_EMAIL"              = "Default FROM email address for SMTP."

    # Twilio Credentials
    "TWILIO_ACCOUNT_SID"           = "Twilio Account SID."
    "TWILIO_AUTH_TOKEN"            = "Twilio Auth Token."
    "TWILIO_PHONE_NO"              = "Twilio phone number."

    # Stripe Credentials
    "STRIPE_API_KEY"               = "Stripe Secret Key."
    "STRIPE_WEBHOOK_SECRET"        = "Stripe Webhook Signing Secret."

    # Other External Services / Miscellaneous Secrets
    "ONESIGNAL_APP_ID"             = "OneSignal App ID."
    "ONESIGNAL_REST_API_KEY"       = "OneSignal REST API Key."
    "SLACK_BOT_TOKEN"              = "Slack Bot Token."
    "SLACK_SIGNING_SECRET"         = "Slack Signing Secret."
    "SLACK_CHANNEL_ID"             = "Default Slack Channel ID for notifications."
    "JWT_SECRET"                   = "Default JWT secret key for application-level tokens."
    "ENCRYPTION_KEY"               = "Default encryption key for application data."
    "SESSION_SECRET_KEY"           = "Session secret key for web applications."
  }
}

variable "gke_secret_accessor_gsa_email_input" {
  description = "The email of the GCP Service Account that GKE Service Accounts will impersonate (via Workload Identity) to access secrets. This GSA is created in iam_service_accounts.tf."
  type        = string
  # Example: "atomic-gke-secret-accessor@${var.gcp_project_id}.iam.gserviceaccount.com"
}

# --- Google Secret Manager Secrets (Placeholders) ---
resource "google_secret_manager_secret" "app_secrets" {
  for_each = var.secrets_to_create_in_gcp_sm

  project   = var.gcp_project_id
  secret_id = each.key # Secret ID (name) in Secret Manager, e.g., POSTGRES_PASSWORD

  replication {
    automatic = true # Automatic replication across regions
  }

  labels = {
    description = substr(each.value, 0, 63) # Use map value as description, truncated if too long for label
    project     = var.project_name
    environment = var.environment_name
    managed-by  = "terraform"
  }
}

# --- Initial Versions for Google Secret Manager Secrets ---
resource "google_secret_manager_secret_version" "app_secrets_initial_version" {
  for_each = google_secret_manager_secret.app_secrets

  secret      = each.value.id # Full ID of the secret resource (projects/PROJECT_ID/secrets/SECRET_ID)
  secret_data = "TO_BE_SET_MANUALLY_OR_VIA_CICD" # Placeholder data

  lifecycle {
    ignore_changes = [secret_data] # Prevents Terraform from overwriting externally managed secret values
  }
}

# --- IAM Bindings for GKE Service Account to access Secrets ---
# Grant the specified GSA (used by GKE Workload Identity) the 'roles/secretmanager.secretAccessor' role for each secret.
resource "google_secret_manager_secret_iam_member" "gke_secret_accessor_binding" {
  for_each = google_secret_manager_secret.app_secrets

  project   = each.value.project # Project ID where the secret exists
  secret_id = each.value.secret_id # The short ID of the secret
  role      = "roles/secretmanager.secretAccessor"
  member    = "serviceAccount:${var.gke_secret_accessor_gsa_email_input}" # GSA email

  # depends_on = [google_service_account.gke_secret_accessor_sa] # If GSA is created in another module/resource in same apply
}

# --- Outputs (typically in outputs.tf) ---
# Moved to outputs.tf as per best practice.
# output "secret_manager_secret_ids_map_output" {
#   description = "A map of the logical secret name (map key) to its full Google Secret Manager ID."
#   value       = { for k, v in google_secret_manager_secret.app_secrets : k => v.id }
# }
#
# output "secret_manager_secret_names_map_output" {
#   description = "A map of the logical secret name (map key) to its short Secret ID (name) in Secret Manager."
#   value       = { for k, v in google_secret_manager_secret.app_secrets : k => v.secret_id }
# }
