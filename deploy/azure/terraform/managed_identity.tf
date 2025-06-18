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
  description = "Name of the project, used for tagging and naming resources."
  type        = string
}

variable "aks_kubelet_mi_name_suffix" {
  description = "Suffix for the User-Assigned Managed Identity for AKS Kubelet."
  type        = string
  default     = "aks-kubelet-mi"
}

variable "acr_id_for_mi_assignment" {
  description = "The ID of the Azure Container Registry for AcrPull role assignment."
  type        = string
  # This would typically be an output from acr.tf: azurerm_container_registry.main.id
}

variable "key_vault_id_for_mi_assignment" {
  description = "The ID of the Azure Key Vault for Key Vault Secrets User role assignment."
  type        = string
  # This would typically be an output from key_vault.tf: azurerm_key_vault.main.id
}

# --- User-Assigned Managed Identity for AKS Kubelet ---
# This identity will be assigned to the AKS node pool(s) (specifically to the kubelet_identity block).
# It allows nodes to pull images from ACR and access Key Vault secrets via the CSI driver.
resource "azurerm_user_assigned_identity" "aks_kubelet_mi" {
  name                = "${var.project_name}-${var.aks_kubelet_mi_name_suffix}"
  resource_group_name = var.resource_group_name
  location            = var.azure_region

  tags = {
    environment = "Production" # Or as per your tagging strategy
    project     = var.project_name
    purpose     = "AKS Kubelet Identity"
    terraform   = "true"
  }
}

# --- Role Assignments for the Kubelet User-Assigned Managed Identity ---

# 1. Grant "AcrPull" role to the Kubelet MI on the ACR scope
resource "azurerm_role_assignment" "kubelet_mi_acr_pull" {
  scope                = var.acr_id_for_mi_assignment
  role_definition_name = "AcrPull" # Built-in role for pulling images from ACR
  principal_id         = azurerm_user_assigned_identity.aks_kubelet_mi.principal_id

  # principal_type is "ServicePrincipal" for User-Assigned Identities.
  # This is often inferred by Azure but can be specified.
  # principal_type       = "ServicePrincipal"
}

# 2. Grant "Key Vault Secrets User" role to the Kubelet MI on the Key Vault scope
# This allows the Secrets Store CSI Driver, using this Kubelet identity, to read secrets.
data "azurerm_role_definition" "key_vault_secrets_user_role_for_mi" { # Renamed data source to avoid conflict if defined elsewhere
  name = "Key Vault Secrets User" # Built-in role
}

resource "azurerm_role_assignment" "kubelet_mi_key_vault_secrets_user" {
  scope                = var.key_vault_id_for_mi_assignment
  role_definition_name = data.azurerm_role_definition.key_vault_secrets_user_role_for_mi.name
  principal_id         = azurerm_user_assigned_identity.aks_kubelet_mi.principal_id
  # principal_type       = "ServicePrincipal"
}

# --- (Optional) Example: Service Principal for CI/CD ---
# This section is commented out and serves as a template for creating a Service Principal
# that a CI/CD system (like GitHub Actions, Jenkins, Azure DevOps) could use to deploy resources.
# This SP would typically need roles like "Contributor" on the resource group, "AcrPush" on ACR,
# and potentially "Key Vault Secrets Officer" or specific secret set permissions.

/*
resource "azuread_application" "cicd_app_registration" {
  display_name = "${var.project_name}-cicd-app"
}

resource "azuread_service_principal" "cicd_sp" {
  application_id = azuread_application.cicd_app_registration.application_id
}

# Store Service Principal password as a secret in Azure Key Vault or GitHub Actions secrets
resource "azuread_service_principal_password" "cicd_sp_password" {
  service_principal_id = azuread_service_principal.cicd_sp.object_id
  description          = "Password for ${var.project_name} CI/CD Service Principal"
  end_date_relative    = "2400h" # e.g., 100 days
}

# Example Role Assignment for CI/CD SP (e.g., AcrPush)
resource "azurerm_role_assignment" "cicd_sp_acr_push" {
  scope                = var.acr_id_for_mi_assignment # Assuming CI/CD pushes to the same ACR
  role_definition_name = "AcrPush"
  principal_id         = azuread_service_principal.cicd_sp.object_id
}

# Example Role Assignment for CI/CD SP (e.g., Key Vault Secrets Officer to set secret values)
data "azurerm_role_definition" "key_vault_secrets_officer_role" {
  name = "Key Vault Secrets Officer"
}
resource "azurerm_role_assignment" "cicd_sp_kv_secrets_officer" {
  scope                = var.key_vault_id_for_mi_assignment
  role_definition_name = data.azurerm_role_definition.key_vault_secrets_officer_role.name
  principal_id         = azuread_service_principal.cicd_sp.object_id
}

# Output CI/CD Service Principal details (handle sensitive data appropriately)
output "cicd_sp_application_id" {
  description = "Application (Client) ID for the CI/CD Service Principal."
  value       = azuread_application.cicd_app_registration.application_id
}
output "cicd_sp_object_id" {
  description = "Object ID for the CI/CD Service Principal."
  value       = azuread_service_principal.cicd_sp.object_id
}
output "cicd_sp_password_value" {
  description = "Password for the CI/CD Service Principal (handle with extreme care - store in secure location)."
  value       = azuread_service_principal_password.cicd_sp_password.value
  sensitive   = true
}
*/

# --- Outputs (typically in outputs.tf) ---
# Moved to outputs.tf as per best practice.
# output "aks_kubelet_user_assigned_identity_id" {
#   description = "The ID of the User-Assigned Managed Identity for AKS Kubelet."
#   value       = azurerm_user_assigned_identity.aks_kubelet_mi.id
# }
#
# output "aks_kubelet_user_assigned_identity_principal_id" {
#   description = "The Principal ID of the User-Assigned Managed Identity for AKS Kubelet. This is needed for role assignments and to assign to AKS node pool."
#   value       = azurerm_user_assigned_identity.aks_kubelet_mi.principal_id
# }
#
# output "aks_kubelet_user_assigned_identity_client_id" {
#   description = "The Client ID of the User-Assigned Managed Identity for AKS Kubelet."
#   value       = azurerm_user_assigned_identity.aks_kubelet_mi.client_id
# }
