variable "project_name" {
  description = "The name of the project, used to prefix secret names."
  type        = string
}

variable "secrets_to_create" {
  description = "A map of secrets to create. Key is the secret name suffix, value is the description."
  type        = map(string)
  default = {
    "POSTGRES_PASSWORD"                = "Password for the RDS PostgreSQL database user."
    "HASURA_GRAPHQL_ADMIN_SECRET"      = "Admin secret for Hasura GraphQL engine."
    "HASURA_GRAPHQL_JWT_SECRET"        = "JWT secret for Hasura GraphQL engine (JSON format)."
    "TRAEFIK_USER"                     = "Username for Traefik dashboard basic authentication."
    "TRAEFIK_PASSWORD"                 = "Password for Traefik dashboard basic authentication."
    "BASIC_AUTH_FUNCTIONS_ADMIN"       = "Basic authentication credentials for -admin endpoints in functions service (e.g., user:pass)."
    "OPENAI_API_KEY"                   = "API key for OpenAI services."
    "GOOGLE_CLIENT_ID_ANDROID"         = "Google Client ID for Android application."
    "GOOGLE_CLIENT_ID_IOS"             = "Google Client ID for iOS application."
    "GOOGLE_CLIENT_ID_WEB"             = "Google Client ID for Web application."
    "GOOGLE_CLIENT_ID_ATOMIC_WEB"      = "Google Client ID for Atomic Web application."
    "GOOGLE_CLIENT_SECRET_ATOMIC_WEB"  = "Google Client Secret for Atomic Web application."
    "GOOGLE_CLIENT_SECRET_WEB"         = "Google Client Secret for Web application."
    "STORAGE_ACCESS_KEY"               = "Access key for Minio/S3 compatible storage."
    "STORAGE_SECRET_KEY"               = "Secret key for Minio/S3 compatible storage."
    "KAFKA_USERNAME"                   = "Username for Kafka SASL authentication (if enabled)."
    "KAFKA_PASSWORD"                   = "Password for Kafka SASL authentication (if enabled)."
    "OPENSEARCH_USERNAME"              = "Username for OpenSearch security (if enabled)."
    "OPENSEARCH_PASSWORD"              = "Password for OpenSearch security (if enabled)."
    "ZOOM_PASS_KEY"                    = "Zoom Pass Key for encryption."
    "ZOOM_CLIENT_ID"                   = "Zoom Client ID."
    "ZOOM_SALT_FOR_PASS"               = "Zoom Salt for Pass Key."
    "ZOOM_IV_FOR_PASS"                 = "Zoom IV for Pass Key."
    "ZOOM_CLIENT_SECRET"               = "Zoom Client Secret."
    "ZOOM_WEBHOOK_SECRET_TOKEN"        = "Zoom Webhook Secret Token."
    "NEXT_PUBLIC_ZOOM_CLIENT_ID"       = "Next.js public Zoom Client ID."
    "OPTAPLANNER_USERNAME"             = "Username for Optaplanner service (shared as API_TOKEN)."
    "OPTAPLANNER_PASSWORD"             = "Password for Optaplanner service (shared as API_TOKEN)."
    "API_TOKEN"                        = "General API Token for custom services (e.g., Optaplanner)."
    # Add more secrets here as needed, for example:
    # "SMTP_HOST"                        = "SMTP server host."
    # "SMTP_PORT"                        = "SMTP server port."
    # "SMTP_USER"                        = "SMTP username."
    # "SMTP_PASSWORD"                    = "SMTP password."
    # "STRIPE_SECRET_KEY"                = "Stripe secret key for payment processing."
    # "TWILIO_ACCOUNT_SID"               = "Twilio Account SID."
    # "TWILIO_AUTH_TOKEN"                = "Twilio Auth Token."
  }
}

resource "aws_secretsmanager_secret" "app_secrets" {
  for_each = var.secrets_to_create

  name        = "${var.project_name}/${each.key}"
  description = each.value
  tags = {
    Name        = "${var.project_name}-${each.key}"
    Project     = var.project_name
    Terraform   = "true"
  }
}

resource "aws_secretsmanager_secret_version" "app_secrets_initial_version" {
  for_each = aws_secretsmanager_secret.app_secrets

  secret_id     = each.value.id
  secret_string = "TO_BE_SET_MANUALLY_OR_VIA_CICD"

  lifecycle {
    ignore_changes = [secret_string] # Ignore changes to secret_string after initial creation
  }
}

output "secret_arns" {
  description = "ARNs of the created secrets."
  value       = { for k, v in aws_secretsmanager_secret.app_secrets : k => v.arn }
}

output "secret_names" {
  description = "Names of the created secrets."
  value       = { for k, v in aws_secretsmanager_secret.app_secrets : k => v.name }
}
