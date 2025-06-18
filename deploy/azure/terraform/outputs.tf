# --- VNet Outputs ---
output "vnet_id_output" {
  description = "The ID of the created Azure Virtual Network."
  value       = azurerm_virtual_network.main.id
}

output "vnet_name_output" {
  description = "The name of the created Azure Virtual Network."
  value       = azurerm_virtual_network.main.name
}

output "aks_subnet_id_output" {
  description = "The ID of the AKS subnet."
  value       = azurerm_subnet.aks.id
}

output "aks_subnet_cidr_output" { # Renamed from aks_subnet_address_prefix for clarity
  description = "The CIDR block of the AKS subnet."
  value       = azurerm_subnet.aks.address_prefixes[0]
}

output "application_subnet_id_output" {
  description = "The ID of the application subnet."
  value       = azurerm_subnet.application.id
}

output "database_subnet_id_output" {
  description = "The ID of the database subnet."
  value       = azurerm_subnet.database.id
}

output "database_subnet_cidr_output" { # Renamed from database_subnet_address_prefix
  description = "The CIDR block of the database subnet."
  value       = azurerm_subnet.database.address_prefixes[0]
}

# --- NSG Outputs ---
output "aks_subnet_nsg_id_output" {
  description = "The ID of the AKS Subnet Network Security Group."
  value       = azurerm_network_security_group.aks_subnet_nsg.id
}

output "db_subnet_nsg_id_output" {
  description = "The ID of the Database Subnet Network Security Group."
  value       = azurerm_network_security_group.db_subnet_nsg.id
}

# --- AKS Cluster Outputs ---
output "aks_cluster_id_output" {
  description = "The ID of the AKS cluster."
  value       = azurerm_kubernetes_cluster.main.id
}

output "aks_cluster_name_output" {
  description = "The name of the AKS cluster."
  value       = azurerm_kubernetes_cluster.main.name
}

output "aks_kube_config_raw_output" {
  description = "Raw Kubeconfig for the AKS cluster. Contains sensitive credentials."
  value       = azurerm_kubernetes_cluster.main.kube_config_raw
  sensitive   = true
}

output "aks_kube_config_output" {
  description = "Kubeconfig block for the AKS cluster (can be used by Kubernetes provider)."
  value       = azurerm_kubernetes_cluster.main.kube_config
  sensitive   = true
}

output "aks_cluster_system_assigned_identity_principal_id_output" {
  description = "The Principal ID of the System Assigned Managed Identity for the AKS cluster control plane."
  value       = azurerm_kubernetes_cluster.main.identity[0].principal_id
}

output "aks_cluster_system_assigned_identity_tenant_id_output" {
  description = "The Tenant ID of the System Assigned Managed Identity for the AKS cluster control plane."
  value       = azurerm_kubernetes_cluster.main.identity[0].tenant_id
}

output "aks_node_resource_group_output" {
  description = "The name of the auto-generated resource group where AKS worker nodes and other resources are deployed."
  value       = azurerm_kubernetes_cluster.main.node_resource_group
}

output "aks_oidc_issuer_url_output" {
  description = "The OIDC issuer URL for the AKS cluster, used for Workload Identity."
  value       = azurerm_kubernetes_cluster.main.oidc_issuer_url
}

# Output for Kubelet Managed Identity (User-Assigned or System-Assigned from default_node_pool)
# This was commented out in aks_cluster.tf as potentially better from a dedicated MI resource.
# If default_node_pool uses SystemAssigned MI (default behavior if no user_assigned_identity_id is set), this is available.
# If default_node_pool is configured to use the User-Assigned MI created in managed_identity.tf,
# then that MI's object_id is what's relevant for KV access by CSI driver using Kubelet identity.
output "aks_default_node_pool_kubelet_identity_object_id_output" {
  description = "Object ID of the Kubelet identity for the default node pool. This is the identity used by the Secrets Store CSI driver if configured to use Kubelet identity."
  value       = azurerm_kubernetes_cluster.main.kubelet_identity[0].object_id
  # Note: This assumes the default_node_pool's Kubelet identity is what's used for KV access by CSI driver.
  # If a separate User-Assigned MI is created and assigned to the node pool for this purpose (as per managed_identity.tf),
  # then that MI's principal_id (aks_kubelet_user_assigned_identity_principal_id_output) is the one to grant KV access to.
}


# --- ACR Outputs ---
output "acr_id_output" {
  description = "The ID of the Azure Container Registry."
  value       = azurerm_container_registry.main.id
}

output "acr_name_output" {
  description = "The name of the Azure Container Registry."
  value       = azurerm_container_registry.main.name
}

output "acr_login_server_output" {
  description = "The login server hostname of the Azure Container Registry."
  value       = azurerm_container_registry.main.login_server
}

# --- Key Vault Outputs ---
output "key_vault_id_output" {
  description = "The ID of the Azure Key Vault."
  value       = azurerm_key_vault.main.id
}

output "key_vault_uri_output" {
  description = "The URI of the Azure Key Vault."
  value       = azurerm_key_vault.main.vault_uri
}

output "key_vault_secret_uris_map_output" {
  description = "A map of the logical secret name to its versionless URI in Azure Key Vault."
  value       = { for k, v in azurerm_key_vault_secret.app_secrets : k => v.versionless_id }
}

output "key_vault_secret_names_map_output" {
  description = "A map of the logical secret name to its name in Key Vault."
  value       = { for k, v in azurerm_key_vault_secret.app_secrets : k => v.name }
}

# --- PostgreSQL Flexible Server Outputs ---
output "pg_flexible_server_id_output" {
  description = "The ID of the PostgreSQL Flexible Server."
  value       = azurerm_postgresql_flexible_server.main.id
}

output "pg_flexible_server_fqdn_output" {
  description = "The Fully Qualified Domain Name of the PostgreSQL Flexible Server."
  value       = azurerm_postgresql_flexible_server.main.fqdn
}

output "pg_flexible_server_admin_username_output" {
  description = "The administrator username for the PostgreSQL Flexible Server."
  value       = azurerm_postgresql_flexible_server.main.administrator_login
}

output "pg_flexible_server_initial_database_name_output" {
  description = "The name of the initial database created on the PostgreSQL Flexible Server."
  value       = azurerm_postgresql_flexible_server_database.initial_db.name
}

output "pg_private_dns_zone_name_output" {
  description = "The name of the private DNS zone created for the PostgreSQL server."
  value       = azurerm_private_dns_zone.pg_private_dns_zone.name
}

# --- Managed Identity Outputs (User-Assigned Kubelet MI) ---
output "aks_kubelet_user_assigned_identity_id_output" {
  description = "The ID of the User-Assigned Managed Identity for AKS Kubelet."
  value       = azurerm_user_assigned_identity.aks_kubelet_mi.id
}

output "aks_kubelet_user_assigned_identity_principal_id_output" {
  description = "The Principal ID of the User-Assigned Managed Identity for AKS Kubelet."
  value       = azurerm_user_assigned_identity.aks_kubelet_mi.principal_id
}

output "aks_kubelet_user_assigned_identity_client_id_output" {
  description = "The Client ID of the User-Assigned Managed Identity for AKS Kubelet."
  value       = azurerm_user_assigned_identity.aks_kubelet_mi.client_id
}
