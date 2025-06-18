# --- Data Sources ---
data "azurerm_client_config" "current" {} # To get current tenant_id if not provided

# --- Variables ---
variable "azure_region" {
  description = "The Azure region where resources will be deployed."
  type        = string
}

variable "resource_group_name" {
  description = "The name of the Azure Resource Group."
  type        = string
}

variable "project_name" {
  description = "The name of the project, used for tagging and naming resources."
  type        = string
}

variable "key_vault_name_suffix" {
  description = "A suffix for the Key Vault name to help ensure global uniqueness. Full name will be <project_name>-kv-<suffix> or similar."
  type        = string
  default     = "kv"
}

variable "key_vault_sku_name" {
  description = "The SKU name for the Azure Key Vault (e.g., 'standard', 'premium')."
  type        = string
  default     = "standard"
  validation {
    condition     = contains(["standard", "premium"], var.key_vault_sku_name)
    error_message = "Key Vault SKU must be either 'standard' or 'premium'."
  }
}

variable "tenant_id" {
  description = "The Azure Tenant ID where the Key Vault and identity reside. If empty, it's fetched from current client config."
  type        = string
  default     = ""
}

variable "secrets_to_create_in_kv" {
  description = "A map of secrets to create in Azure Key Vault. Key is the secret name (use hyphens), value is a description."
  type        = map(string)
  default = {
    # Database Secrets
    "POSTGRES-USER"                = "Username for Azure Database for PostgreSQL."
    "POSTGRES-PASSWORD"            = "Password for Azure Database for PostgreSQL."

    # Hasura Secrets
    "HASURA-GRAPHQL-ADMIN-SECRET"  = "Admin secret for Hasura GraphQL engine."
    "HASURA-GRAPHQL-JWT-SECRET"    = "JWT secret for Hasura GraphQL engine (JSON format)."

    # Storage/MinIO Secrets (generic names if secrets are for MinIO running in K8s, or for Azure Blob HMAC keys)
    "STORAGE-ACCESS-KEY"           = "Access key for Azure Blob Storage HMAC or MinIO."
    "STORAGE-SECRET-KEY"           = "Secret key for Azure Blob Storage HMAC or MinIO."

    # Functions Service Secrets (examples)
    "OPENAI-API-KEY"               = "API key for OpenAI services."
    "BASIC-AUTH-FUNCTIONS-ADMIN"   = "Basic authentication credentials for -admin endpoints in functions service."
    "GOOGLE-CLIENT-ID-ATOMIC-WEB"  = "Google Client ID for Atomic Web application (used by Functions)." # This is a Google OAuth Client ID
    "ZOOM-CLIENT-SECRET"           = "Zoom Client Secret (used by Functions)."

    # Other secrets, using hyphens for Key Vault secret names
    "TRAEFIK-USER"                     = "Username for Traefik dashboard basic authentication."
    "TRAEFIK-PASSWORD"                 = "Password for Traefik dashboard basic authentication."
    "GOOGLE-CLIENT-ID-ANDROID"         = "Google Client ID for Android application."
    "GOOGLE-CLIENT-ID-IOS"             = "Google Client ID for iOS application."
    "GOOGLE-CLIENT-SECRET-ATOMIC-WEB"  = "Google Client Secret for Atomic Web application."
    "GOOGLE-CLIENT-SECRET-WEB"         = "Google Client Secret for Web application."
    "KAFKA-USERNAME"                   = "Username for Kafka SASL authentication (if enabled)."
    "KAFKA-PASSWORD"                   = "Password for Kafka SASL authentication (if enabled)."
    "OPENSEARCH-USERNAME"              = "Username for OpenSearch security (if enabled)."
    "OPENSEARCH-PASSWORD"              = "Password for OpenSearch security (if enabled)."
    "ZOOM-PASS-KEY"                    = "Zoom Pass Key for encryption."
    "ZOOM-CLIENT-ID"                   = "Zoom Client ID."
    "ZOOM-SALT-FOR-PASS"               = "Zoom Salt for Pass Key."
    "ZOOM-IV-FOR-PASS"                 = "Zoom IV for Pass Key."
    "ZOOM-WEBHOOK-SECRET-TOKEN"        = "Zoom Webhook Secret Token."
    "API-TOKEN"                        = "General API Token for custom services (e.g., Optaplanner)."
  }
}

variable "aks_secret_accessor_identity_object_id" {
  description = "The Object ID of the Managed Identity (User-Assigned for Kubelet/CSI-driver-specific, or System-Assigned for AKS control plane if that's used by CSI driver) that needs access to Key Vault secrets."
  type        = string
  # This should be the object_id of the identity that the Secrets Store CSI Driver Azure provider will use.
  # If using User-Assigned MI for node pools (and Kubelet uses it for KV access), this is its object ID.
  # If using Workload Identity for the CSI driver's own SA, this would be the GSA's object ID (less common for Azure KV provider).
  # For AKS Key Vault Addon, it often uses the Kubelet identity.
}

# --- Azure Key Vault Name Construction ---
# Key Vault names must be globally unique, 3-24 alphanumeric characters and hyphens, start with letter, end with letter/digit.
locals {
  # Attempt to create a compliant Key Vault name.
  # Users should verify this or provide a fully compliant key_vault_name directly if this logic is insufficient.
  raw_kv_name      = lower("${var.project_name}-kv-${var.key_vault_name_suffix}")
  sanitized_kv_name = substr(replace(local.raw_kv_name, "/[^a-z0-9-]/", ""), 0, 24) # Remove non-alphanumeric-hyphen, limit length
  # Further ensure it starts with a letter and doesn't end with a hyphen (more complex regex might be needed for perfect auto-compliance)
  # For simplicity, this basic sanitization is provided. User should ensure `project_name` and `key_vault_name_suffix` are sensible.
  final_key_vault_name = length(local.sanitized_kv_name) < 3 ? "kv${local.sanitized_kv_name}xyz" : local.sanitized_kv_name # Ensure min length
}


# --- Azure Key Vault Resource ---
resource "azurerm_key_vault" "main" {
  name                        = local.final_key_vault_name
  location                    = var.azure_region
  resource_group_name         = var.resource_group_name
  tenant_id                   = var.tenant_id != "" ? var.tenant_id : data.azurerm_client_config.current.tenant_id
  sku_name                    = var.key_vault_sku_name
  enable_rbac_authorization   = true       # Enable RBAC for Key Vault data plane operations (recommended)

  soft_delete_retention_days  = 7
  purge_protection_enabled    = true # Recommended for production

  network_acls {
    default_action = "Deny"    # Deny by default
    bypass         = "AzureServices" # Allows trusted Azure services to bypass
    # ip_rules       = []      # Allowed IP addresses/ranges (e.g., for CI/CD to populate secrets)
    # virtual_network_subnet_ids = [] # Subnets that can access KV (e.g., if not using private endpoints)
  }

  tags = {
    environment = "Production" # Or as per your tagging strategy
    project     = var.project_name
    terraform   = "true"
  }
}

# --- Azure Key Vault Secrets (Placeholders) ---
resource "azurerm_key_vault_secret" "app_secrets" {
  for_each = var.secrets_to_create_in_kv

  name         = each.key # Secret name in Key Vault (e.g., "POSTGRES-PASSWORD")
  value        = "TO-BE-SET-MANUALLY-OR-VIA-CICD" # Placeholder value
  key_vault_id = azurerm_key_vault.main.id
  content_type = "text/plain" # Or "application/json" if storing JSON stringified objects

  lifecycle {
    ignore_changes = [value] # Prevents Terraform from overwriting externally managed secret values
  }

  tags = {
    description = each.value # Use the map value as a description tag
    project     = var.project_name
    terraform   = "true"
  }
}

# --- RBAC Role Assignment for AKS Identity to access Key Vault Secrets ---
# This allows the identity used by the Secrets Store CSI Driver Azure provider
# (e.g., Kubelet MI or a User-Assigned MI for node pool) to read secrets.

data "azurerm_role_definition" "key_vault_secrets_user_role" {
  name = "Key Vault Secrets User" # Built-in role for reading secret contents
}

resource "azurerm_role_assignment" "aks_identity_to_key_vault_secrets" {
  scope                = azurerm_key_vault.main.id # Assign role at the Key Vault scope
  role_definition_name = data.azurerm_role_definition.key_vault_secrets_user_role.name
  principal_id         = var.aks_secret_accessor_identity_object_id # Object ID of the MI (Kubelet MI or other)

  # principal_type can be "User", "Group", or "ServicePrincipal".
  # For Managed Identities (System-Assigned or User-Assigned), the principal_id is that of a Service Principal.
  # principal_type       = "ServicePrincipal" # Often optional, Azure can infer for MI object IDs.
}

# --- Outputs (typically in outputs.tf) ---
# Moved to outputs.tf as per best practice.
# output "key_vault_id" {
#   description = "The ID of the Azure Key Vault."
#   value       = azurerm_key_vault.main.id
# }
#
# output "key_vault_uri" {
#   description = "The URI of the Azure Key Vault."
#   value       = azurerm_key_vault.main.vault_uri
# }
#
# output "key_vault_secret_uris_map" { # Changed from _ids to _uris for more direct usability sometimes
#   description = "A map of the logical secret name to its full URI in Azure Key Vault."
#   value       = { for k, v in azurerm_key_vault_secret.app_secrets : k => v.versionless_id } # versionless_id points to latest
# }
#
# output "key_vault_secret_names_map" {
#   description = "A map of the logical secret name to its name in Key Vault."
#   value       = { for k, v in azurerm_key_vault_secret.app_secrets : k => v.name }
# }
