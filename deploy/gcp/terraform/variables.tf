# --- Global GCP Configuration Variables ---
# These are already defined in provider.tf for provider configuration,
# but listed here for completeness as central input variables.
# variable "gcp_project_id" { ... }
# variable "gcp_region" { ... }

variable "gcp_zone" {
  description = "The GCP zone for zonal resources or primary zone for regional GKE cluster. If null/empty for GKE, a regional cluster is assumed."
  type        = string
  default     = null # e.g., "us-central1-a"
}

variable "project_name" {
  description = "A short name for your project (e.g., atomic, myapp). Used for resource prefixing, naming, and labeling."
  type        = string
  default     = "atomic"
}

variable "environment_name" {
  description = "Deployment environment (e.g., dev, staging, prod). Used for tagging, labeling, and resource naming."
  type        = string
  default     = "dev"
}

# --- VPC Network Configuration Variables ---
variable "vpc_network_name" {
  description = "The name for the VPC network."
  type        = string
  default     = "atomic-vpc" # From vpc_network.tf
}

variable "gke_subnet_name" {
  description = "The name for the GKE subnet."
  type        = string
  default     = "gke-subnet" # From vpc_network.tf
}

variable "gke_subnet_cidr" {
  description = "The primary CIDR block for the GKE subnet."
  type        = string
  default     = "10.2.0.0/20" # From vpc_network.tf
}

variable "gke_pods_cidr_name" {
  description = "The name for the GKE Pods secondary IP range."
  type        = string
  default     = "gke-pods-range" # From vpc_network.tf
}

variable "gke_pods_cidr_block" {
  description = "The CIDR block for the GKE Pods secondary IP range."
  type        = string
  default     = "10.3.0.0/16" # From vpc_network.tf
}

variable "gke_services_cidr_name" {
  description = "The name for the GKE Services secondary IP range."
  type        = string
  default     = "gke-services-range" # From vpc_network.tf
}

variable "gke_services_cidr_block" {
  description = "The CIDR block for the GKE Services secondary IP range."
  type        = string
  default     = "10.4.0.0/20" # From vpc_network.tf
}

variable "db_subnet_name" {
  description = "The name for the Database subnet (e.g., for Cloud SQL private IP)."
  type        = string
  default     = "db-subnet" # From vpc_network.tf
}

variable "db_subnet_cidr" {
  description = "The CIDR block for the Database subnet."
  type        = string
  default     = "10.5.0.0/24" # From vpc_network.tf
}

variable "vpc_network_cidr_block_for_firewall" {
  description = "A representative CIDR block for the entire VPC network for internal firewall rules."
  type        = string
  default     = "10.0.0.0/8" # From vpc_network.tf
}

# --- GKE Cluster Configuration Variables ---
variable "gke_cluster_name_suffix" {
  description = "Suffix for the GKE cluster name."
  type        = string
  default     = "cluster" # From gke_cluster.tf
}

variable "gke_kubernetes_version" {
  description = "Desired Kubernetes version for the GKE cluster (specific version or release channel like 'regular')."
  type        = string
  default     = "1.27" # From gke_cluster.tf
}

variable "gke_primary_node_pool_name" {
  description = "Name for the primary GKE node pool."
  type        = string
  default     = "primary-nodes" # From gke_cluster.tf
}

variable "gke_primary_node_initial_count" {
  description = "Initial number of nodes per zone in the primary node pool."
  type        = number
  default     = 1 # From gke_cluster.tf
}

variable "gke_primary_node_machine_type" {
  description = "Machine type for the nodes in the primary GKE node pool."
  type        = string
  default     = "e2-medium" # From gke_cluster.tf
}

# --- Artifact Registry Configuration Variables ---
variable "artifact_registry_repository_name" {
  description = "The name for the Artifact Registry repository."
  type        = string
  default     = "atomic-app-images" # Example from artifact_registry.tf (user needs to ensure uniqueness or use random)
}

# --- Secret Manager Configuration Variables ---
variable "secrets_to_create_in_gcp_sm" {
  description = "A map of secrets to create in Google Secret Manager. Key is secret_id (underscores), value is description."
  type        = map(string)
  default = { # Default from secret_manager.tf
    "POSTGRES_USER"                = "Username for Cloud SQL for PostgreSQL."
    "POSTGRES_PASSWORD"            = "Password for Cloud SQL for PostgreSQL."
    "HASURA_GRAPHQL_ADMIN_SECRET"  = "Admin secret for Hasura GraphQL engine."
    "HASURA_GRAPHQL_JWT_SECRET"    = "JWT secret for Hasura GraphQL engine (JSON format)."
    "TRAEFIK_USER"                 = "Username for Traefik dashboard basic authentication."
    "TRAEFIK_PASSWORD"             = "Password for Traefik dashboard basic authentication."
    "BASIC_AUTH_FUNCTIONS_ADMIN"   = "Basic authentication credentials for -admin endpoints in functions service."
    "OPENAI_API_KEY"               = "API key for OpenAI services."
    "API_TOKEN"                    = "General API Token for custom services (e.g., Optaplanner)."
    "GOOGLE_CLIENT_ID_ANDROID"     = "Google OAuth Client ID for Android application."
    "GOOGLE_CLIENT_ID_IOS"         = "Google OAuth Client ID for iOS application."
    "GOOGLE_CLIENT_ID_WEB"         = "Google OAuth Client ID for Web application."
    "GOOGLE_CLIENT_ID_ATOMIC_WEB"  = "Google OAuth Client ID for Atomic Web application."
    "GOOGLE_CLIENT_SECRET_ATOMIC_WEB" = "Google OAuth Client Secret for Atomic Web application."
    "GOOGLE_CLIENT_SECRET_WEB"     = "Google OAuth Client Secret for Web application."
    "GOOGLE_CALENDAR_ID"           = "Google Calendar ID for integration."
    "GOOGLE_CALENDAR_CREDENTIALS"  = "Google Calendar service account credentials JSON string (for app use)."
    "GOOGLE_MAP_KEY"               = "Google Maps API Key."
    "GOOGLE_PLACE_API_KEY"         = "Google Places API Key."
    "STORAGE_ACCESS_KEY"           = "Access key for MinIO or GCS HMAC."
    "STORAGE_SECRET_KEY"           = "Secret key for MinIO or GCS HMAC."
    "STORAGE_REGION"               = "Default region for S3 compatible storage (e.g., us-east1 if GCS)."
    "KAFKA_USERNAME"               = "Username for Kafka SASL authentication."
    "KAFKA_PASSWORD"               = "Password for Kafka SASL authentication."
    "OPENSEARCH_USERNAME"          = "Username for OpenSearch security."
    "OPENSEARCH_PASSWORD"          = "Password for OpenSearch security."
    "ZOOM_CLIENT_ID"               = "Zoom Client ID."
    "ZOOM_CLIENT_SECRET"           = "Zoom Client Secret."
    "ZOOM_PASS_KEY"                = "Zoom Pass Key for encryption."
    "ZOOM_SALT_FOR_PASS"           = "Zoom Salt for Pass Key."
    "ZOOM_IV_FOR_PASS"             = "Zoom IV for Pass Key."
    "ZOOM_WEBHOOK_SECRET_TOKEN"    = "Zoom Webhook Secret Token."
    "OPTAPLANNER_USERNAME"         = "Username for Optaplanner service."
    "OPTAPLANNER_PASSWORD"         = "Password for Optaplanner service (often same as API_TOKEN)."
    "SMTP_HOST"                    = "SMTP server host."
    "SMTP_PORT"                    = "SMTP server port."
    "SMTP_USER"                    = "SMTP username."
    "SMTP_PASS"                    = "SMTP password."
    "SMTP_FROM_EMAIL"              = "Default FROM email address for SMTP."
    "TWILIO_ACCOUNT_SID"           = "Twilio Account SID."
    "TWILIO_AUTH_TOKEN"            = "Twilio Auth Token."
    "TWILIO_PHONE_NO"              = "Twilio phone number."
    "STRIPE_API_KEY"               = "Stripe Secret Key."
    "STRIPE_WEBHOOK_SECRET"        = "Stripe Webhook Signing Secret."
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

# --- Cloud SQL for PostgreSQL Configuration Variables ---
variable "cloudsql_instance_name_suffix" {
  description = "Suffix for the Cloud SQL instance name."
  type        = string
  default     = "pgs01" # From cloudsql_postgres.tf
}

variable "cloudsql_database_version" {
  description = "The version of PostgreSQL to use for Cloud SQL."
  type        = string
  default     = "POSTGRES_14" # From cloudsql_postgres.tf
}

variable "cloudsql_tier" {
  description = "The machine type or tier for the Cloud SQL instance."
  type        = string
  default     = "db-f1-micro" # From cloudsql_postgres.tf
}

variable "cloudsql_disk_size" {
  description = "The size of the disk (in GB) for the Cloud SQL instance."
  type        = number
  default     = 20 # From cloudsql_postgres.tf
}

variable "cloudsql_initial_db_name" {
  description = "The name of the initial database on the Cloud SQL instance."
  type        = string
  default     = "atomicdb" # From cloudsql_postgres.tf
}

variable "cloudsql_main_user_name" {
  description = "The name for the main user of the Cloud SQL instance."
  type        = string
  default     = "pgatomicadmin" # From cloudsql_postgres.tf
}

variable "cloudsql_main_user_password_secret_id" {
  description = "The Secret ID (short name) in Google Secret Manager for the Cloud SQL main user's password."
  type        = string
  default     = "POSTGRES_PASSWORD" # From cloudsql_postgres.tf, must match a key in secrets_to_create_in_gcp_sm
}

variable "cloudsql_private_ip_range_name" {
  description = "The name for the reserved IP address range for Service Networking."
  type        = string
  default     = "google-managed-services-range" # From cloudsql_postgres.tf
}

variable "cloudsql_private_ip_range_cidr" {
  description = "The CIDR block for the reserved IP address range for Service Networking."
  type        = string
  default     = "10.6.0.0/24" # From cloudsql_postgres.tf
}

variable "cloudsql_backup_retention_days" {
  description = "Number of backups to retain for Cloud SQL."
  type        = number
  default     = 7 # From cloudsql_postgres.tf
}

variable "cloudsql_multi_az" {
  description = "Whether to enable High Availability (regional instance) for Cloud SQL."
  type        = bool
  default     = false # From cloudsql_postgres.tf
}

# --- IAM Service Accounts Configuration Variables ---
variable "gke_node_sa_account_id_suffix" {
  description = "Suffix for the GKE Node Service Account ID."
  type        = string
  default     = "node-sa" # From iam_service_accounts.tf
}

variable "gke_workload_identity_sa_account_id_suffix" {
  description = "Suffix for the GKE Workload Identity Service Account ID."
  type        = string
  default     = "wi-sa" # From iam_service_accounts.tf
}

variable "enable_cicd_sa_gcp" {
  description = "Set to true to create a dedicated Service Account for CI/CD."
  type        = bool
  default     = false # From iam_service_accounts.tf
}

variable "cicd_sa_account_id_suffix_gcp" {
  description = "Suffix for the CI/CD Service Account ID if enabled."
  type        = string
  default     = "cicd-sa" # From iam_service_accounts.tf
}

variable "example_ksa_namespace" {
  description = "Placeholder: Kubernetes namespace of a KSA that will use Workload Identity."
  type        = string
  default     = "default" # From iam_service_accounts.tf
}

variable "example_ksa_name" {
  description = "Placeholder: Kubernetes Service Account name that will use Workload Identity."
  type        = string
  default     = "default" # From iam_service_accounts.tf
}

# --- Variables for linking resources (outputs from one resource used as input to another) ---
# In a monolithic setup, these are less "input" variables and more like local assignments.
# However, defining them here shows what would be needed if modularized.
# Their default values are `null` as they should be populated by resource outputs.

variable "vpc_network_self_link_for_dependencies" {
  description = "The self_link of the VPC network, used by Cloud SQL and GKE."
  type        = string
  default     = null # Populated by google_compute_network.main.self_link
}

variable "gke_subnet_name_for_gke" {
  description = "The name of the GKE subnetwork, used by GKE cluster."
  type        = string
  default     = null # Populated by google_compute_subnetwork.gke_subnet.name
}

variable "gke_pods_range_name_for_gke" {
  description = "The name of the GKE Pods secondary IP range, used by GKE cluster."
  type        = string
  default     = null # Populated by google_compute_subnetwork.gke_subnet.secondary_ip_range[0].range_name or var.gke_pods_cidr_name
}

variable "gke_services_range_name_for_gke" {
  description = "The name of the GKE Services secondary IP range, used by GKE cluster."
  type        = string
  default     = null # Populated by google_compute_subnetwork.gke_subnet.secondary_ip_range[1].range_name or var.gke_services_cidr_name
}

variable "gke_node_sa_email_for_gke_nodepool_and_ar" { # Renamed from gke_node_service_account_email_input to avoid confusion
  description = "Email of the GKE Node Service Account, used by GKE node pool and Artifact Registry IAM."
  type        = string
  default     = null # Populated by google_service_account.gke_node_sa.email
}

variable "gke_secret_accessor_gsa_email_for_sm" { # Renamed from gke_secret_accessor_gsa_email_input
  description = "Email of the GKE Workload Identity GSA, used for Secret Manager IAM."
  type        = string
  default     = null # Populated by google_service_account.gke_workload_identity_sa.email
}

variable "cicd_sa_email_for_ar" { # Renamed from cicd_service_account_email_gcp_input
  description = "Email of the CI/CD GSA, used for Artifact Registry IAM (if enabled)."
  type        = string
  default     = null # Populated by google_service_account.cicd_sa[0].email if enabled
}


# --- Data Sources ---
# data "google_client_config" "default" {} # To get current project, region if not provided
# data "google_project" "current" { # To get project details like project_number
#   project_id = var.gcp_project_id
# }

# --- Random ID for Suffixing Globally Unique Resources ---
# This helps in creating unique names for resources that require it globally, like ACR, Key Vault, PG Server.
# The actual resource name construction will be: ${var.project_name}-${var.some_suffix}-${random_id.gcp_resource_suffix.hex}
resource "random_id" "gcp_resource_suffix" {
  byte_length = 4 # Creates an 8-character hex string
}
