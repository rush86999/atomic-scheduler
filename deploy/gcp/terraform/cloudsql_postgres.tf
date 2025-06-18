# --- Variables ---
variable "gcp_project_id" {
  description = "The GCP project ID where Cloud SQL will be deployed."
  type        = string
}

variable "gcp_region" {
  description = "The GCP region for the Cloud SQL instance."
  type        = string
}

variable "project_name" {
  description = "Name of the project, used for tagging and naming resources."
  type        = string
}

variable "cloudsql_instance_name_suffix" {
  description = "Suffix for the Cloud SQL instance name. Full name e.g. ${var.project_name}-pgs-${var.cloudsql_instance_name_suffix}."
  type        = string
  default     = "pgs01" # PostgreSQL Server 01
}

variable "cloudsql_database_version" {
  description = "The version of PostgreSQL to use (e.g., POSTGRES_14)."
  type        = string
  default     = "POSTGRES_14" # Check GCP for latest supported versions
}

variable "cloudsql_tier" { # Machine type / tier
  description = "The machine type or tier for the Cloud SQL instance (e.g., db-f1-micro, db-g1-small, custom tiers)."
  type        = string
  default     = "db-f1-micro" # Smallest tier, suitable for dev/test
}

variable "cloudsql_disk_size" {
  description = "The size of the disk (in GB) for the Cloud SQL instance."
  type        = number
  default     = 20 # Minimum is typically 10GB or 20GB depending on tier/region
}

variable "cloudsql_initial_db_name" {
  description = "The name of the initial database to be created on the Cloud SQL instance."
  type        = string
  default     = "atomicdb"
}

variable "cloudsql_main_user_name" {
  description = "The name for the main user of the Cloud SQL instance."
  type        = string
  default     = "pgatomicadmin"
}

variable "cloudsql_main_user_password_secret_id" {
  description = "The Secret ID (short name, not full resource name) of the secret in Google Secret Manager that stores the main user's password."
  type        = string
  default     = "POSTGRES_PASSWORD" # Must match a secret_id created in secret_manager.tf
}

variable "vpc_network_self_link_input" { # Renamed to avoid conflict with potential output
  description = "The self_link of the VPC network to which Cloud SQL will be peered for private IP access."
  type        = string
  # This would typically be an output from vpc_network.tf: google_compute_network.main.self_link
}

variable "cloudsql_private_ip_range_name" {
  description = "The name for the reserved IP address range for Service Networking (Cloud SQL private IP)."
  type        = string
  default     = "google-managed-services-range" # A common name
}

variable "cloudsql_private_ip_range_cidr" {
  description = "The CIDR block for the reserved IP address range for Service Networking. Must not overlap with existing subnets."
  type        = string
  default     = "10.6.0.0/24" # Example, ensure this is a free range in your VPC
}

variable "cloudsql_backup_retention_days" {
  description = "Number of backups to retain. 0 disables backups."
  type        = number
  default     = 7
}

variable "cloudsql_multi_az" {
  description = "Whether to enable High Availability (regional instance) for Cloud SQL. Requires certain tiers."
  type        = bool
  default     = false # For dev/test. Set to true for production with appropriate tier.
}

# --- Cloud SQL Instance Name Construction ---
locals {
  # Cloud SQL instance names must be unique within a project.
  # Max 80 chars, lowercase letters, numbers, hyphens. Must start with letter.
  raw_cloudsql_instance_name    = lower("${var.project_name}-pgs-${var.cloudsql_instance_name_suffix}")
  sanitized_cloudsql_instance_name = substr(replace(local.raw_cloudsql_instance_name, "/[^a-z0-9-]/", ""), 0, 80)
  # Ensure it starts with a letter (more complex regex might be needed for perfect auto-compliance)
  final_cloudsql_instance_name = length(local.sanitized_cloudsql_instance_name) < 1 ? "pgs-instance-${random_string.global_suffix.result}" : ( substr(local.sanitized_cloudsql_instance_name, 0, 1) == "-" ? "p${local.sanitized_cloudsql_instance_name}" : local.sanitized_cloudsql_instance_name )
}

# --- Fetch Main User Password from Google Secret Manager ---
data "google_secret_manager_secret_version" "cloudsql_main_user_password" {
  project = var.gcp_project_id
  secret  = var.cloudsql_main_user_password_secret_id # Short ID of the secret

  # version can be "latest" or a specific version number. "latest" is common.
  version = "latest"

  # depends_on = [google_secret_manager_secret_version.app_secrets_initial_version] # If secret version is created in same TF apply
}

# --- Reserve IP Range for Service Networking (Cloud SQL Private IP) ---
resource "google_compute_global_address" "private_ip_address" {
  project      = var.gcp_project_id
  name         = var.cloudsql_private_ip_range_name
  purpose      = "VPC_PEERING"
  address_type = "INTERNAL"
  address      = var.cloudsql_private_ip_range_cidr # The first IP of this range is used by the service producer
  network      = var.vpc_network_self_link_input    # The VPC network to associate with
}

# --- VPC Peering for Service Networking ---
resource "google_service_networking_connection" "private_vpc_connection" {
  project                 = var.gcp_project_id # Ensure this is the host project if using Shared VPC
  network                 = var.vpc_network_self_link_input
  service                 = "servicenetworking.googleapis.com" # Google service that manages the peering
  reserved_peering_ranges = [google_compute_global_address.private_ip_address.name] # Name of the reserved range

  # This resource can take time to provision and might need to be created before Cloud SQL instance
  # that depends on private networking. Explicit dependency might be needed on Cloud SQL instance.
}

# --- Google Cloud SQL for PostgreSQL Instance ---
resource "google_sql_database_instance" "main" {
  project          = var.gcp_project_id
  name             = local.final_cloudsql_instance_name
  region           = var.gcp_region
  database_version = var.cloudsql_database_version # e.g., POSTGRES_14

  settings {
    tier    = var.cloudsql_tier # e.g., db-f1-micro or db-custom-CPU-RAM
    disk_size = var.cloudsql_disk_size # In GB
    disk_type = "PD_SSD" # Or PD_HDD

    # IP Configuration for Private IP
    ip_configuration {
      ipv4_enabled    = false # Disable public IP
      private_network = var.vpc_network_self_link_input
      # require_ssl     = true # Enforce SSL for connections (good for security)
      # allocated_ip_range = google_compute_global_address.private_ip_address.name # Not directly set here for Cloud SQL, uses the peering.
    }

    backup_configuration {
      enabled            = var.cloudsql_backup_retention_days > 0
      binary_log_enabled = false # For PostgreSQL, typically not needed unless using point-in-time recovery with external replicas
      backup_retention_period_days = var.cloudsql_backup_retention_days # Days
      # start_time         = "03:00" # HH:MM format in UTC
      # location           = var.gcp_region # Or a specific backup location
    }

    # High Availability (Regional instance)
    availability_type = var.cloudsql_multi_az ? "REGIONAL" : "ZONAL"
    # insights_config { # Query Insights
    #   query_insights_enabled = true
    #   query_string_length    = 1024
    # }
    # database_flags {
    #   name  = "cloudsql.iam_authentication" # Enable IAM database authentication
    #   value = "on"
    # }
  }

  # Deletion protection should be enabled for production instances
  # deletion_protection = true

  # Ensure Service Networking connection is established before creating the instance with private IP
  depends_on = [google_service_networking_connection.private_vpc_connection]
}

# --- Initial Logical Database ---
resource "google_sql_database" "main_db" {
  project  = var.gcp_project_id
  instance = google_sql_database_instance.main.name
  name     = var.cloudsql_initial_db_name
  charset  = "UTF8"
  collation = "en_US.UTF8" # Or your preferred collation
}

# --- Main User for the Database ---
resource "google_sql_user" "main_user" {
  project  = var.gcp_project_id
  instance = google_sql_database_instance.main.name
  name     = var.cloudsql_main_user_name
  password = data.google_secret_manager_secret_version.cloudsql_main_user_password.secret_data # Fetched from Secret Manager

  # host can be restricted if needed, e.g., "%" for any host, or specific IP/range
  # type = "BUILT_IN" # For PostgreSQL, this is the default and often only option for main user
}

# --- Outputs (typically in outputs.tf) ---
# Moved to outputs.tf as per best practice.
# output "cloudsql_instance_name_output" {
#   description = "The name of the Cloud SQL PostgreSQL instance."
#   value       = google_sql_database_instance.main.name
# }
#
# output "cloudsql_instance_connection_name" {
#   description = "The connection name of the Cloud SQL instance (for Cloud SQL Proxy, etc.)."
#   value       = google_sql_database_instance.main.connection_name
# }
#
# output "cloudsql_instance_private_ip_address" {
#   description = "The private IP address of the Cloud SQL instance."
#   value       = google_sql_database_instance.main.private_ip_address
# }
#
# output "cloudsql_main_db_name_output" {
#   description = "The name of the main logical database created in Cloud SQL."
#   value       = google_sql_database.main_db.name
# }
#
# output "cloudsql_main_user_name_output" {
#   description = "The name of the main user for the Cloud SQL instance."
#   value       = google_sql_user.main_user.name
# }
#
# output "cloudsql_service_networking_connection_name" {
#   description = "The name of the service networking connection for VPC peering."
#   value       = google_service_networking_connection.private_vpc_connection.peering
# }
