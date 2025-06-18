# --- Variables ---
variable "azure_region" {
  description = "The Azure region where the PostgreSQL Flexible Server will be deployed."
  type        = string
}

variable "resource_group_name" {
  description = "The name of the Azure Resource Group."
  type        = string
}

variable "project_name" {
  description = "Name of the project, used for tagging and naming resources."
  type        = string
}

variable "pg_server_name_suffix" {
  description = "A suffix for the PostgreSQL Flexible Server name to help ensure global uniqueness. Full name will be <project_name>-pgs-<suffix>."
  type        = string
  default     = "pgs01" # PostgreSQL Server 01
}

variable "pg_admin_username" {
  description = "Administrator username for the PostgreSQL Flexible Server."
  type        = string
  default     = "pgatomicadmin"
}

variable "pg_sku_name" {
  description = "SKU name for the PostgreSQL Flexible Server (e.g., GP_Standard_D2s_v3, B_Standard_B1ms)."
  type        = string
  default     = "B_Standard_B1ms" # Burstable, good for dev/test or low-load prod
}

variable "pg_version" {
  description = "PostgreSQL major version (e.g., 13, 14, 15, 16)."
  type        = string
  default     = "14" # Specify a recent, supported version
}

variable "pg_storage_mb" {
  description = "Storage size in MB for the PostgreSQL Flexible Server."
  type        = number
  default     = 32768 # 32 GB (minimum for some SKUs/features)
}

variable "pg_backup_retention_days" {
  description = "Backup retention period in days (7-35 days)."
  type        = number
  default     = 7
}

variable "pg_geo_redundant_backup_enabled" {
  description = "Enable geo-redundant backups."
  type        = bool
  default     = false # Set to true for production disaster recovery needs
}

variable "db_subnet_id" {
  description = "The ID of the subnet to which the PostgreSQL Flexible Server should be delegated (output from vnet.tf)."
  type        = string
  # Example: azurerm_subnet.database.id
}

variable "vnet_id_for_dns_link" { # Added this variable
  description = "The ID of the Virtual Network to link with the Private DNS Zone."
  type        = string
  # Example: azurerm_virtual_network.main.id
}

variable "pg_initial_database_name" {
  description = "The name of the initial database to be created on the PostgreSQL server."
  type        = string
  default     = "atomicdb"
}

# Key Vault Integration Variables
variable "key_vault_id_for_pg_password" {
  description = "The ID of the Azure Key Vault where the PostgreSQL admin password secret is stored."
  type        = string
  # Example: azurerm_key_vault.main.id (from key_vault.tf)
}

variable "pg_admin_password_secret_name" {
  description = "The name of the secret in Azure Key Vault that stores the PostgreSQL admin password."
  type        = string
  default     = "POSTGRES-PASSWORD" # Must match the secret name created in key_vault.tf
}

# --- PostgreSQL Server Name Construction ---
locals {
  # PostgreSQL Flexible Server names must be globally unique.
  raw_pg_server_name    = lower("${var.project_name}-${var.pg_server_name_suffix}")
  # Replace non-alphanumeric except hyphens, ensure it starts/ends with alphanumeric, limit length (3-63 chars).
  # This is a simplified sanitization. A more robust regex might be needed for all edge cases.
  sanitized_pg_server_name = substr(replace(local.raw_pg_server_name, "/[^a-z0-9-]/", ""), 0, 63)
  # Ensure it doesn't start or end with a hyphen (can be complex with just substr/replace)
  # For simplicity, users should ensure project_name and suffix result in a valid start/end.
  final_pg_server_name = length(local.sanitized_pg_server_name) < 3 ? "pgs${local.sanitized_pg_server_name}xyz" : local.sanitized_pg_server_name
}

# --- Data Source to Fetch PostgreSQL Admin Password from Azure Key Vault ---
data "azurerm_key_vault_secret" "pg_admin_password" {
  name         = var.pg_admin_password_secret_name
  key_vault_id = var.key_vault_id_for_pg_password
}

# --- Private DNS Zone for PostgreSQL Flexible Server ---
# Using a private DNS zone allows resolution of the server's FQDN to its private IP within the VNet.
# The zone name is typically <server_name>.postgres.database.azure.com for Flexible Server.
resource "azurerm_private_dns_zone" "pg_private_dns_zone" {
  name                = "${local.final_pg_server_name}.postgres.database.azure.com"
  resource_group_name = var.resource_group_name # Often good to place DNS zone in same RG as VNet or a dedicated DNS RG

  tags = {
    environment = "Production"
    project     = var.project_name
    terraform   = "true"
  }
}

# --- Link Private DNS Zone to the Virtual Network ---
resource "azurerm_private_dns_zone_virtual_network_link" "pg_dns_vnet_link" {
  name                  = "${local.final_pg_server_name}-vnet-link"
  resource_group_name   = var.resource_group_name
  private_dns_zone_name = azurerm_private_dns_zone.pg_private_dns_zone.name
  virtual_network_id    = var.vnet_id_for_dns_link # Link to the main VNet
  registration_enabled  = false # Auto-registration not typically used for PaaS like Flexible Server's own records

  tags = {
    environment = "Production"
    project     = var.project_name
    terraform   = "true"
  }
}

# --- Azure Database for PostgreSQL Flexible Server ---
resource "azurerm_postgresql_flexible_server" "main" {
  name                   = local.final_pg_server_name
  resource_group_name    = var.resource_group_name
  location               = var.azure_region
  version                = var.pg_version
  administrator_login    = var.pg_admin_username
  administrator_password = data.azurerm_key_vault_secret.pg_admin_password.value # Fetched from Key Vault

  delegated_subnet_id    = var.db_subnet_id # VNet Integration using subnet delegation
  private_dns_zone_id    = azurerm_private_dns_zone.pg_private_dns_zone.id # Link to our private DNS zone

  sku_name               = var.pg_sku_name
  storage_mb             = var.pg_storage_mb

  backup_retention_days  = var.pg_backup_retention_days
  geo_redundant_backup_enabled = var.pg_geo_redundant_backup_enabled

  # Ensure public network access is disabled to rely on VNet integration
  public_network_access_enabled = false

  # Availability zone (optional, can be set to 1, 2, or 3 if region supports it and SKU allows)
  # zone = "1"

  # High availability (optional, requires certain SKUs and regions)
  # high_availability {
  #   mode = "ZoneRedundant" # Or "SameZone"
  #   # standby_availability_zone = "2" # If mode is ZoneRedundant and you want to specify standby AZ
  # }

  tags = {
    environment = "Production"
    project     = var.project_name
    terraform   = "true"
  }

  depends_on = [
    data.azurerm_key_vault_secret.pg_admin_password,
    azurerm_private_dns_zone_virtual_network_link.pg_dns_vnet_link # Ensure DNS setup is ready
  ]
}

# --- Initial Database Creation ---
resource "azurerm_postgresql_flexible_server_database" "initial_db" {
  name      = var.pg_initial_database_name
  server_id = azurerm_postgresql_flexible_server.main.id
  charset   = "UTF8"
  collation = "en_US.utf8" # Or your preferred collation
}

# --- Outputs (typically in outputs.tf) ---
# Moved to outputs.tf as per best practice.
# output "pg_flexible_server_id" {
#   description = "The ID of the PostgreSQL Flexible Server."
#   value       = azurerm_postgresql_flexible_server.main.id
# }
#
# output "pg_flexible_server_fqdn" {
#   description = "The Fully Qualified Domain Name of the PostgreSQL Flexible Server. Resolves to a private IP within the VNet."
#   value       = azurerm_postgresql_flexible_server.main.fqdn
# }
#
# output "pg_flexible_server_admin_username" {
#   description = "The administrator username for the PostgreSQL Flexible Server."
#   value       = azurerm_postgresql_flexible_server.main.administrator_login # or var.pg_admin_username
# }
#
# output "pg_flexible_server_initial_database_name" {
#   description = "The name of the initial database created on the PostgreSQL Flexible Server."
#   value       = azurerm_postgresql_flexible_server_database.initial_db.name
# }
#
# output "pg_private_dns_zone_name" {
#   description = "The name of the private DNS zone created for the PostgreSQL server."
#   value       = azurerm_private_dns_zone.pg_private_dns_zone.name
# }
