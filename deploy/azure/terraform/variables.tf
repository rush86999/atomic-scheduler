# --- Global Azure Configuration Variables ---
variable "azure_region" {
  description = "The Azure region where all resources will be deployed."
  type        = string
  default     = "East US" # Example default, adjust as needed
}

variable "resource_group_name" {
  description = "The name of the Azure Resource Group to deploy resources into. It's assumed this RG already exists or is created separately."
  type        = string
  # default     = "atomic-rg" # Example: Should be created or specified by user.
}

variable "project_name" {
  description = "A short name for your project (e.g., atomic, myapp). Used for resource prefixing and tagging."
  type        = string
  default     = "atomic"
}

variable "environment_name" {
  description = "Deployment environment (e.g., dev, staging, prod). Used for tagging and resource naming."
  type        = string
  default     = "dev"
}

variable "tenant_id" {
  description = "The Azure Tenant ID where resources reside. If empty, it's fetched from current client config."
  type        = string
  default     = "" # Will use data.azurerm_client_config.current.tenant_id if empty
}

# --- VNet and Subnet Configuration Variables ---
variable "vnet_cidr" {
  description = "The CIDR block for the Azure Virtual Network."
  type        = string
  default     = "10.1.0.0/16"
}

variable "aks_subnet_cidr" {
  description = "The CIDR block for the AKS subnet."
  type        = string
  default     = "10.1.1.0/24"
}

variable "application_subnet_cidr" {
  description = "The CIDR block for the general application subnet."
  type        = string
  default     = "10.1.2.0/24"
}

variable "database_subnet_cidr" {
  description = "The CIDR block for the database subnet."
  type        = string
  default     = "10.1.3.0/24"
}

# --- NSG Configuration Variables (aks_subnet_cidr already defined) ---
# NSG names are derived from project_name.

# --- AKS Cluster Configuration Variables ---
variable "aks_cluster_name_suffix" {
  description = "Suffix for the AKS cluster name. Full name will be ${var.project_name}-aks-${var.aks_cluster_name_suffix}-${var.environment_name} or similar."
  type        = string
  default     = "cluster"
}

variable "aks_kubernetes_version" {
  description = "Desired Kubernetes version for the AKS cluster."
  type        = string
  default     = "1.27.9" # Check Azure for latest supported patch versions.
}

variable "aks_default_node_pool_name" {
  description = "Name for the default node pool in AKS."
  type        = string
  default     = "agentpool"
}

variable "aks_default_node_count" {
  description = "Initial number of nodes in the default node pool."
  type        = number
  default     = 2
}

variable "aks_default_node_vm_size" {
  description = "VM size for the nodes in the default node pool."
  type        = string
  default     = "Standard_DS2_v2"
}

variable "log_analytics_workspace_id" {
  description = "The resource ID of the Log Analytics workspace for Azure Monitor for containers. Optional. If not provided, AKS monitoring might use a default or not be fully enabled."
  type        = string
  default     = null
}

variable "aks_service_cidr" {
  description = "CIDR for Kubernetes services within AKS."
  type        = string
  default     = "10.2.0.0/16"
}

variable "aks_dns_service_ip" {
  description = "IP address for the DNS service within AKS (must be within service_cidr)."
  type        = string
  default     = "10.2.0.10"
}

variable "aks_docker_bridge_cidr" {
  description = "CIDR for the Docker bridge network on nodes."
  type        = string
  default     = "172.17.0.1/16"
}

# --- ACR (Azure Container Registry) Configuration Variables ---
variable "acr_name_suffix" {
  description = "A suffix for the ACR name to help ensure global uniqueness. Full name will be <project_name><suffix>."
  type        = string
  default     = "acr"
}

variable "acr_sku" {
  description = "The SKU for the Azure Container Registry (Basic, Standard, Premium)."
  type        = string
  default     = "Standard"
  validation {
    condition     = contains(["Basic", "Standard", "Premium"], var.acr_sku)
    error_message = "ACR SKU must be one of: Basic, Standard, Premium."
  }
}

# --- Key Vault Configuration Variables ---
variable "key_vault_name_suffix" {
  description = "A suffix for the Key Vault name to help ensure global uniqueness. Full name will be <project_name>-kv-<suffix> or similar."
  type        = string
  default     = "kv"
}

variable "key_vault_sku_name" {
  description = "The SKU name for the Azure Key Vault (standard or premium)."
  type        = string
  default     = "standard"
  validation {
    condition     = contains(["standard", "premium"], var.key_vault_sku_name)
    error_message = "Key Vault SKU must be either 'standard' or 'premium'."
  }
}

variable "secrets_to_create_in_kv" {
  description = "A map of secrets to create in Azure Key Vault. Key is secret name (hyphenated), value is description."
  type        = map(string)
  default = { # Default from key_vault.tf
    "POSTGRES-USER"                = "Username for Azure Database for PostgreSQL."
    "POSTGRES-PASSWORD"            = "Password for Azure Database for PostgreSQL."
    "HASURA-GRAPHQL-ADMIN-SECRET"  = "Admin secret for Hasura GraphQL engine."
    "HASURA-GRAPHQL-JWT-SECRET"    = "JWT secret for Hasura GraphQL engine (JSON format)."
    "STORAGE-ACCESS-KEY"           = "Access key for Azure Blob Storage HMAC or MinIO."
    "STORAGE-SECRET-KEY"           = "Secret key for Azure Blob Storage HMAC or MinIO."
    "OPENAI-API-KEY"               = "API key for OpenAI services."
    "BASIC-AUTH-FUNCTIONS-ADMIN"   = "Basic authentication credentials for -admin endpoints in functions service."
    "GOOGLE-CLIENT-ID-ATOMIC-WEB"  = "Google Client ID for Atomic Web application."
    "ZOOM-CLIENT-SECRET"           = "Zoom Client Secret."
    "TRAEFIK-USER"                 = "Username for Traefik dashboard basic authentication."
    "TRAEFIK-PASSWORD"             = "Password for Traefik dashboard basic authentication."
    "GOOGLE-CLIENT-ID-ANDROID"     = "Google Client ID for Android application."
    "GOOGLE-CLIENT-ID-IOS"         = "Google Client ID for iOS application."
    "GOOGLE-CLIENT-SECRET-ATOMIC-WEB" = "Google Client Secret for Atomic Web application."
    "GOOGLE-CLIENT-SECRET-WEB"     = "Google Client Secret for Web application."
    "KAFKA-USERNAME"               = "Username for Kafka SASL authentication."
    "KAFKA-PASSWORD"               = "Password for Kafka SASL authentication."
    "OPENSEARCH-USERNAME"          = "Username for OpenSearch security."
    "OPENSEARCH-PASSWORD"          = "Password for OpenSearch security."
    "ZOOM-PASS-KEY"                = "Zoom Pass Key for encryption."
    "ZOOM-CLIENT-ID"               = "Zoom Client ID."
    "ZOOM-SALT-FOR-PASS"           = "Zoom Salt for Pass Key."
    "ZOOM-IV-FOR-PASS"             = "Zoom IV for Pass Key."
    "ZOOM-WEBHOOK-SECRET-TOKEN"    = "Zoom Webhook Secret Token."
    "API-TOKEN"                    = "General API Token for custom services."
  }
}

# --- PostgreSQL Flexible Server Configuration Variables ---
variable "pg_server_name_suffix" {
  description = "A suffix for the PostgreSQL Flexible Server name. Full name will be <project_name>-pgs-<suffix>."
  type        = string
  default     = "pgs01"
}

variable "pg_admin_username" {
  description = "Administrator username for the PostgreSQL Flexible Server."
  type        = string
  default     = "pgatomicadmin"
}

variable "pg_sku_name" {
  description = "SKU name for the PostgreSQL Flexible Server (e.g., GP_Standard_D2s_v3, B_Standard_B1ms)."
  type        = string
  default     = "B_Standard_B1ms"
}

variable "pg_version" {
  description = "PostgreSQL major version (e.g., 13, 14, 15, 16)."
  type        = string
  default     = "14"
}

variable "pg_storage_mb" {
  description = "Storage size in MB for the PostgreSQL Flexible Server."
  type        = number
  default     = 32768 # 32 GB
}

variable "pg_backup_retention_days" {
  description = "Backup retention period in days."
  type        = number
  default     = 7
}

variable "pg_geo_redundant_backup_enabled" {
  description = "Enable geo-redundant backups for PostgreSQL."
  type        = bool
  default     = false
}

variable "pg_initial_database_name" {
  description = "The name of the initial database to be created on the PostgreSQL server."
  type        = string
  default     = "atomicdb"
}

variable "pg_admin_password_secret_name_in_kv" { # Renamed for clarity
  description = "The name of the secret in Azure Key Vault that stores the PostgreSQL admin password."
  type        = string
  default     = "POSTGRES-PASSWORD" # Must match a key in secrets_to_create_in_kv
}

# --- Managed Identity Configuration Variables ---
variable "aks_kubelet_mi_name_suffix" {
  description = "Suffix for the User-Assigned Managed Identity for AKS Kubelet."
  type        = string
  default     = "aks-kubelet-mi"
}

# IDs for linking resources (these will be outputs from other resources, not direct user inputs in a monolithic setup)
# For a modular setup, these would be actual input variables.
# For this flat structure, they are effectively local references.
# To make this variables.tf self-contained for potential modular use, they are defined.
# However, their defaults would typically not be set or would be `null`.
# For now, leaving them without defaults as they are meant to be dynamically populated.

variable "aks_subnet_id_input" { # Used by aks_cluster.tf
  description = "The ID of the subnet where AKS nodes will be deployed. (Typically output from vnet.tf)"
  type        = string
  default = null # No default, must be provided or derived from azurerm_subnet.aks.id
}

variable "db_subnet_id_input" { # Used by postgresql.tf
  description = "The ID of the subnet to which the PostgreSQL Flexible Server should be delegated. (Typically output from vnet.tf)"
  type        = string
  default = null
}

variable "vnet_id_for_dns_link_input" { # Used by postgresql.tf
  description = "The ID of the Virtual Network to link with the Private DNS Zone for PostgreSQL. (Typically output from vnet.tf)"
  type        = string
  default = null
}

variable "key_vault_id_for_pg_password_input" { # Used by postgresql.tf
  description = "The ID of the Azure Key Vault where the PostgreSQL admin password secret is stored. (Typically output from key_vault.tf)"
  type        = string
  default = null
}

variable "aks_cluster_system_assigned_identity_principal_id_input" { # Used by acr.tf
  description = "The Principal ID of the System Assigned Managed Identity of the AKS cluster. Used for granting AcrPull role. (Typically output from aks_cluster.tf)"
  type        = string
  default = null
}

variable "acr_id_for_mi_assignment_input" { # Used by managed_identity.tf
  description = "The ID of the Azure Container Registry for AcrPull role assignment to Kubelet MI. (Typically output from acr.tf)"
  type        = string
  default = null
}

variable "key_vault_id_for_mi_assignment_input" { # Used by managed_identity.tf
  description = "The ID of the Azure Key Vault for Key Vault Secrets User role assignment to Kubelet MI. (Typically output from key_vault.tf)"
  type        = string
  default = null
}

variable "aks_kubelet_mi_object_id_input" { # Used by key_vault.tf (as aks_secret_accessor_identity_object_id)
  description = "The Object ID of the User-Assigned Managed Identity for Kubelet (or other MI for KV access). (Typically output from managed_identity.tf)"
  type        = string
  default = null
}


# --- Data Sources ---
# To get current tenant_id if not explicitly provided by var.tenant_id
data "azurerm_client_config" "current" {}

# Resource for generating random strings for globally unique names
resource "random_string" "global_suffix" {
  length  = 6
  special = false
  upper   = false
}
