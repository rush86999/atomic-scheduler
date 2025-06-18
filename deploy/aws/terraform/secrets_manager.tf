# --- Variables ---
variable "project_name" {
  description = "Name of the project, used to prefix secret names in AWS Secrets Manager."
  type        = string
}

variable "secrets_to_create" {
  description = "A map of secrets to create in AWS Secrets Manager. Key is the secret name suffix, value is the description."
  type        = map(string)
  default = {
    # Database Secrets
    "POSTGRES_USER"                = "Username for the RDS PostgreSQL database." # Or a generic DB user if not RDS specific
    "POSTGRES_PASSWORD"            = "Password for the RDS PostgreSQL database user."

    # Hasura Secrets
    "HASURA_GRAPHQL_ADMIN_SECRET"  = "Admin secret for Hasura GraphQL engine."
    "HASURA_GRAPHQL_JWT_SECRET"    = "JWT secret for Hasura GraphQL engine (JSON format)." # Store as a JSON string

    # Traefik Basic Auth (if dashboard is protected)
    "TRAEFIK_USER"                 = "Username for Traefik dashboard basic authentication."
    "TRAEFIK_PASSWORD"             = "Password for Traefik dashboard basic authentication."

    # Functions Service Basic Auth (for -admin endpoints)
    "BASIC_AUTH_FUNCTIONS_ADMIN"   = "Basic authentication credentials for -admin endpoints in functions service (e.g., user:pass)."

    # API Keys
    "OPENAI_API_KEY"               = "API key for OpenAI services."
    "API_TOKEN"                    = "General API Token for custom services (e.g., used by Optaplanner as password)." # Also used by Optaplanner

    # Google OAuth & Service Credentials
    "GOOGLE_CLIENT_ID_ANDROID"         = "Google Client ID for Android application."
    "GOOGLE_CLIENT_ID_IOS"             = "Google Client ID for iOS application."
    "GOOGLE_CLIENT_ID_WEB"             = "Google Client ID for Web application."
    "GOOGLE_CLIENT_ID_ATOMIC_WEB"      = "Google Client ID for Atomic Web application." # Used by 'app' and 'functions'
    "GOOGLE_CLIENT_SECRET_ATOMIC_WEB"  = "Google Client Secret for Atomic Web application."
    "GOOGLE_CLIENT_SECRET_WEB"         = "Google Client Secret for Web application."
    "GOOGLE_CALENDAR_ID"               = "Google Calendar ID for integration."
    "GOOGLE_CALENDAR_CREDENTIALS"      = "Google Calendar service account credentials JSON string."
    "GOOGLE_MAP_KEY"                   = "Google Maps API Key."
    "GOOGLE_PLACE_API_KEY"             = "Google Places API Key."

    # Storage (Minio/S3 compatible) Secrets
    "STORAGE_ACCESS_KEY"           = "Access key for Minio or S3 compatible storage." # e.g., MINIO_ROOT_USER
    "STORAGE_SECRET_KEY"           = "Secret key for Minio or S3 compatible storage." # e.g., MINIO_ROOT_PASSWORD
    "STORAGE_REGION"               = "Default region for S3 compatible storage (e.g., us-east-1)."


    # Kafka Credentials (if SASL is enabled)
    "KAFKA_USERNAME"               = "Username for Kafka SASL authentication."
    "KAFKA_PASSWORD"               = "Password for Kafka SASL authentication."

    # OpenSearch Credentials (if security is enabled)
    "OPENSEARCH_USERNAME"          = "Username for OpenSearch security."
    "OPENSEARCH_PASSWORD"          = "Password for OpenSearch security."

    # Zoom Integration Secrets
    "ZOOM_CLIENT_ID"               = "Zoom Client ID." # Used by 'oauth' and 'functions'
    "ZOOM_CLIENT_SECRET"           = "Zoom Client Secret." # Used by 'oauth' and 'functions'
    "ZOOM_PASS_KEY"                = "Zoom Pass Key for encryption." # Used by 'app', 'oauth', 'functions'
    "ZOOM_SALT_FOR_PASS"           = "Zoom Salt for Pass Key." # Used by 'oauth', 'functions'
    "ZOOM_IV_FOR_PASS"             = "Zoom IV for Pass Key." # Used by 'app', 'oauth', 'functions'
    "ZOOM_WEBHOOK_SECRET_TOKEN"    = "Zoom Webhook Secret Token." # Used by 'oauth', 'functions'
    # NEXT_PUBLIC_ZOOM_CLIENT_ID is usually a public var, but if sensitive part of it, include here. Assuming it's distinct or covered by ZOOM_CLIENT_ID.

    # Optaplanner Credentials (Note: API_TOKEN is often used as OPTAPLANNER_PASSWORD)
    "OPTAPLANNER_USERNAME"         = "Username for Optaplanner service."
    "OPTAPLANNER_PASSWORD"         = "Password for Optaplanner service (can be same as API_TOKEN)."

    # SMTP / Email Service Credentials
    "SMTP_HOST"                    = "SMTP server host."
    "SMTP_PORT"                    = "SMTP server port (e.g., 587, 465)."
    "SMTP_USER"                    = "SMTP username."
    "SMTP_PASS"                    = "SMTP password."
    "SMTP_FROM_EMAIL"              = "Default FROM email address for SMTP."

    # Twilio Credentials
    "TWILIO_ACCOUNT_SID"           = "Twilio Account SID."
    "TWILIO_AUTH_TOKEN"            = "Twilio Auth Token."
    "TWILIO_PHONE_NO"              = "Twilio phone number."

    # Stripe Credentials
    "STRIPE_API_KEY"               = "Stripe Secret Key." # Server-side secret key
    "STRIPE_WEBHOOK_SECRET"        = "Stripe Webhook Signing Secret."
    # STRIPE_PUBLIC_KEY is usually public and handled by frontend.

    # Other External Services / Miscellaneous Secrets
    "ONESIGNAL_APP_ID"             = "OneSignal App ID."
    "ONESIGNAL_REST_API_KEY"       = "OneSignal REST API Key."
    "SLACK_BOT_TOKEN"              = "Slack Bot Token."
    "SLACK_SIGNING_SECRET"         = "Slack Signing Secret."
    "SLACK_CHANNEL_ID"             = "Default Slack Channel ID for notifications."
    "JWT_SECRET"                   = "Default JWT secret key for application-level tokens." # If functions uses its own JWTs
    "ENCRYPTION_KEY"               = "Default encryption key for application data."
    "SESSION_SECRET_KEY"           = "Session secret key for web applications (e.g. oauth service)."
  }
}

# --- AWS Secrets Manager Secrets ---
resource "aws_secretsmanager_secret" "main" {
  for_each = var.secrets_to_create

  name        = "${var.project_name}/${each.key}" # Full secret name/path
  description = each.value                        # Description for the secret

  tags = {
    Name        = "${var.project_name}-${each.key}" # Tag for easier identification
    Project     = var.project_name
    Terraform   = "true"
  }
}

# --- Initial Versions for AWS Secrets Manager Secrets ---
resource "aws_secretsmanager_secret_version" "main_initial_version" {
  for_each = aws_secretsmanager_secret.main

  secret_id     = each.value.id # ARN or name of the secret
  secret_string = "TO_BE_SET_MANUALLY_OR_VIA_CICD" # Placeholder value

  # This lifecycle block is crucial. It tells Terraform to ignore changes to the secret_string
  # after the initial creation. This allows the actual secret values to be populated and managed
  # outside of Terraform (e.g., manually, by a CI/CD pipeline, or another automated process)
  # without Terraform trying to revert them to the placeholder value on subsequent applies.
  lifecycle {
    ignore_changes = [secret_string]
  }
}

# --- Outputs (typically in outputs.tf) ---
# Moved to outputs.tf as per best practice.
# output "secret_arns" {
#   description = "Map of secret name suffixes to their full ARNs in AWS Secrets Manager."
#   value = {
#     for k, v in aws_secretsmanager_secret.main :
#     k => v.arn
#   }
# }
#
# output "secret_names" {
#   description = "Map of secret name suffixes to their full names in AWS Secrets Manager."
#   value = {
#     for k, v in aws_secretsmanager_secret.main :
#     k => v.name
#   }
# }
