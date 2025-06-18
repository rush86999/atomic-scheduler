# --- Variables ---
variable "azure_region" {
  description = "The Azure region where the ACR will be deployed."
  type        = string
}

variable "resource_group_name" {
  description = "The name of the Azure Resource Group where ACR will be deployed."
  type        = string
}

variable "project_name" {
  description = "Name of the project, used for tagging and constructing the ACR name."
  type        = string
}

variable "acr_name_suffix" {
  description = "A suffix for the ACR name to help ensure global uniqueness. The full name will be <project_name><suffix>."
  type        = string
  default     = "acr" # Example: myprojectacr
}

variable "acr_sku" {
  description = "The SKU for the Azure Container Registry (e.g., Basic, Standard, Premium)."
  type        = string
  default     = "Standard" # Standard is a good balance for most use cases
  validation {
    condition     = contains(["Basic", "Standard", "Premium"], var.acr_sku)
    error_message = "ACR SKU must be one of: Basic, Standard, Premium."
  }
}

variable "aks_cluster_system_assigned_identity_principal_id" {
  description = "The Principal ID of the System Assigned Managed Identity of the AKS cluster. Used for granting AcrPull role."
  type        = string
  # This value would typically be an output from the aks_cluster.tf module:
  # azurerm_kubernetes_cluster.main.identity[0].principal_id
}


# --- Azure Container Registry (ACR) ---
# ACR names must be globally unique, alphanumeric, and 5-50 characters.
# We'll construct a name and then sanitize it.
locals {
  # Attempt to create a compliant ACR name. Users should verify this or provide a fully compliant acr_name directly.
  raw_acr_name    = lower("${var.project_name}${var.acr_name_suffix}")
  sanitized_acr_name = substr(replace(local.raw_acr_name, "/[^a-z0-9]/", ""), 0, 50) # Remove non-alphanumeric, take first 50 chars
}

resource "azurerm_container_registry" "main" {
  name                = local.sanitized_acr_name # Globally unique name for the ACR
  resource_group_name = var.resource_group_name
  location            = var.azure_region
  sku                 = var.acr_sku
  admin_enabled       = false # Recommended to disable admin user and use token/service principal/MI based auth

  # Optional: Geo-replications for high availability and performance across regions
  # georeplications {
  #   location                = "East US" # Example additional region
  #   zone_redundancy_enabled = true      # If the region supports AZs
  #   tags                    = {}
  # }

  # Optional: Network rule set for restricting access
  # network_rule_set {
  #   default_action = "Deny" # Deny by default
  #   ip_rule = [
  #     {
  #       action = "Allow"
  #       ip_range = "YOUR_CI_CD_IP_RANGE" # Example: Allow CI/CD IP
  #     }
  #   ]
  #   # virtual_network allows access from specific VNet subnets (e.g., for AKS if using private link)
  # }

  tags = {
    environment = "Production" # Or as per your tagging strategy
    project     = var.project_name
    terraform   = "true"
  }
}

# --- IAM Role Assignment for AKS to Pull from ACR ---
# Grant the AKS cluster's system-assigned managed identity the "AcrPull" role on this ACR.
# This allows Kubelet on AKS nodes to pull images from this ACR.

resource "azurerm_role_assignment" "aks_pull_from_acr" {
  scope                = azurerm_container_registry.main.id
  role_definition_name = "AcrPull" # Built-in role for pulling images
  principal_id         = var.aks_cluster_system_assigned_identity_principal_id

  # The principal_type for a System-Assigned Managed Identity is "ServicePrincipal".
  # principal_type       = "ServicePrincipal" # Optional: can often be inferred

  # Note: If you later configure AKS node pools to use User-Assigned Managed Identities (UMIs)
  # for Kubelet, you would need to grant the "AcrPull" role to those UMIs instead of,
  # or in addition to, the cluster's system-assigned identity.
  # This setup assumes the cluster's main identity (or the default Kubelet identity derived from it)
  # is sufficient for pulling images. This is common for basic AKS setups.
  # If a dedicated User-Assigned MI is created for Kubelet (e.g., in managed_identity.tf),
  # its principal_id should be used here instead.
}

# --- Outputs (typically in outputs.tf) ---
# Moved to outputs.tf as per best practice.
# output "acr_id" {
#   description = "The ID of the Azure Container Registry."
#   value       = azurerm_container_registry.main.id
# }
#
# output "acr_name" {
#   description = "The name of the Azure Container Registry."
#   value       = azurerm_container_registry.main.name
# }
#
# output "acr_login_server" {
#   description = "The login server hostname of the Azure Container Registry (e.g., myacr.azurecr.io)."
#   value       = azurerm_container_registry.main.login_server
# }
