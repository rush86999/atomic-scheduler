# Variables to be defined in variables.tf or passed as input
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

variable "key_vault_name" {
  description = "The name of the Azure Key Vault. Must be globally unique."
  type        = string
}

variable "key_vault_sku_name" {
  description = "The SKU name for the Azure Key Vault (e.g., 'standard', 'premium')."
  type        = string
  default     = "standard"
}

variable "tenant_id" {
  description = "The Azure Tenant ID where the Key Vault and identity reside."
  type        = string
  # This can often be sourced from the Azure provider configuration or data source:
  # data "azurerm_client_config" "current" {}
  # default = data.azurerm_client_config.current.tenant_id
}

variable "secrets_to_create_in_kv" {
  description = "A map of secrets to create in Azure Key Vault. Key is the secret name, value is a description (or can be an object with more attributes if needed)."
  type        = map(string)
  default = {
    # Database Secrets
    "POSTGRES-USER"                = "Username for Azure Database for PostgreSQL." # Or reference to a specific user identity if managed separately
    "POSTGRES-PASSWORD"            = "Password for Azure Database for PostgreSQL."

    # Hasura Secrets
    "HASURA-GRAPHQL-ADMIN-SECRET"  = "Admin secret for Hasura GraphQL engine."
    # HASURA_GRAPHQL_JWT_SECRET is a JSON structure. Key Vault can store it as a string.
    "HASURA-GRAPHQL-JWT-SECRET"    = "JWT secret for Hasura GraphQL engine (JSON format)."

    # MinIO/Storage Secrets
    "STORAGE-ACCESS-KEY"           = "Access key for Azure Blob Storage or MinIO."
    "STORAGE-SECRET-KEY"           = "Secret key for Azure Blob Storage or MinIO."

    # Functions Service Secrets (examples)
    "OPENAI-API-KEY"               = "API key for OpenAI services."
    "BASIC-AUTH-FUNCTIONS-ADMIN"   = "Basic authentication credentials for -admin endpoints in functions service (e.g., user:pass)."
    "GOOGLE-CLIENT-ID-ATOMIC-WEB"  = "Google Client ID for Atomic Web application (used by Functions)."
    "ZOOM-CLIENT-SECRET"           = "Zoom Client Secret (used by Functions)."

    # Add other secrets based on the list from the AWS secrets_manager.tf, adapting names for Key Vault if needed.
    # Hyphens are generally preferred over underscores in Azure resource naming conventions where possible for secrets.
    "TRAEFIK-USER"                     = "Username for Traefik dashboard basic authentication."
    "TRAEFIK-PASSWORD"                 = "Password for Traefik dashboard basic authentication."
    "GOOGLE-CLIENT-ID-ANDROID"         = "Google Client ID for Android application."
    "GOOGLE-CLIENT-ID-IOS"             = "Google Client ID for iOS application."
    # "GOOGLE_CLIENT_ID_WEB"           - already covered by ATOMIC_WEB or similar, or add if distinct
    "GOOGLE-CLIENT-SECRET-ATOMIC-WEB"  = "Google Client Secret for Atomic Web application."
    "GOOGLE-CLIENT-SECRET-WEB"         = "Google Client Secret for Web application."
    "KAFKA-USERNAME"                   = "Username for Kafka SASL authentication (if enabled)."
    "KAFKA-PASSWORD"                   = "Password for Kafka SASL authentication (if enabled)."
    "OPENSEARCH-USERNAME"              = "Username for OpenSearch security (if enabled)."
    "OPENSEARCH-PASSWORD"              = "Password for OpenSearch security (if enabled)."
    "ZOOM-PASS-KEY"                    = "Zoom Pass Key for encryption."
    "ZOOM-CLIENT-ID"                   = "Zoom Client ID." # Used by oauth and potentially functions
    "ZOOM-SALT-FOR-PASS"               = "Zoom Salt for Pass Key."
    "ZOOM-IV-FOR-PASS"                 = "Zoom IV for Pass Key."
    "ZOOM-WEBHOOK-SECRET-TOKEN"        = "Zoom Webhook Secret Token."
    "API-TOKEN"                        = "General API Token for custom services (e.g., Optaplanner)."
  }
}

variable "aks_kubelet_identity_object_id" {
  description = "The Object ID of the AKS Kubelet's Managed Identity (e.g., user-assigned MI for agentpool) or the AKS Cluster's main identity that CSI driver will use."
  type        = string
  # This should be an output from the AKS cluster resource definition (e.g., azurerm_kubernetes_cluster.main.kubelet_identity[0].object_id)
  # or the specific identity configured for the Secrets Store CSI Driver Azure provider.
}

# --- Azure Key Vault Resource ---
resource "azurerm_key_vault" "main" {
  name                        = var.key_vault_name
  location                    = var.azure_region
  resource_group_name         = var.resource_group_name
  tenant_id                   = var.tenant_id # Explicitly set tenant_id
  sku_name                    = var.key_vault_sku_name # e.g., "standard" or "premium"
  enable_rbac_authorization   = true       # Enable RBAC for Key Vault data plane operations

  # Soft delete and purge protection are recommended for production
  soft_delete_retention_days  = 7 # Or your desired retention period (7-90 days)
  purge_protection_enabled    = true # Recommended for production to prevent accidental permanent deletion

  network_acls {
    default_action = "Deny"    # Deny by default
    bypass         = "AzureServices" # Allows trusted Azure services to bypass
    # ip_rules       = []      # Allowed IP addresses/ranges (e.g., for CI/CD access if needed)
    # virtual_network_subnet_ids = [] # Allowed VNet subnets (e.g., for specific service access if not using private endpoints)
  }

  tags = {
    environment = "Production"
    project     = var.project_name
    terraform   = "true"
  }
}

# --- Azure Key Vault Secrets (Placeholders) ---
resource "azurerm_key_vault_secret" "app_secrets" {
  for_each = var.secrets_to_create_in_kv

  name         = each.key # Secret name in Key Vault
  value        = "TO-BE-SET-MANUALLY-OR-VIA-CICD" # Placeholder value
  key_vault_id = azurerm_key_vault.main.id
  content_type = "text/plain" # Or application/json if storing JSON

  # This lifecycle block ensures that Terraform does not try to revert changes
  # made to the secret value outside of Terraform after initial creation.
  lifecycle {
    ignore_changes = [value]
  }

  tags = {
    description = each.value # Use the map value as a description tag
    project     = var.project_name
    terraform   = "true"
  }
}

# --- RBAC Role Assignment for AKS Kubelet Identity to access Key Vault Secrets ---
# This allows the identity used by the Secrets Store CSI Driver Azure provider (often Kubelet MI)
# to read secrets from this Key Vault.

# Data source to get built-in role definition for "Key Vault Secrets User"
data "azurerm_role_definition" "key_vault_secrets_user" {
  name = "Key Vault Secrets User" # Built-in role
}

resource "azurerm_role_assignment" "aks_to_key_vault_secrets" {
  scope                = azurerm_key_vault.main.id # Assign role at the Key Vault scope
  role_definition_name = data.azurerm_role_definition.key_vault_secrets_user.name
  principal_id         = var.aks_kubelet_identity_object_id # Object ID of the AKS Kubelet MI or other designated identity

  # The principal_type for a User-Assigned Managed Identity is "ServicePrincipal".
  # If using a System-Assigned Managed Identity (e.g., from the AKS cluster itself),
  # its object_id would also typically correspond to a ServicePrincipal representing that identity.
  # principal_type       = "ServicePrincipal" # Optional: can often be inferred
}

# Outputs
output "key_vault_id" {
  description = "The ID of the Azure Key Vault."
  value       = azurerm_key_vault.main.id
}

output "key_vault_uri" {
  description = "The URI of the Azure Key Vault."
  value       = azurerm_key_vault.main.vault_uri
}

output "key_vault_secret_ids" {
  description = "A map of the IDs of the created Key Vault secrets."
  value       = { for k, v in azurerm_key_vault_secret.app_secrets : k => v.id }
}
