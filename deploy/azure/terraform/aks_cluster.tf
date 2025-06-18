# --- Variables ---
variable "azure_region" {
  description = "The Azure region where the AKS cluster will be deployed."
  type        = string
}

variable "resource_group_name" {
  description = "The name of the Azure Resource Group where AKS will be deployed."
  type        = string
}

variable "project_name" {
  description = "Name of the project, used for tagging and naming resources."
  type        = string
}

variable "aks_cluster_name" {
  description = "The name for the AKS cluster."
  type        = string
}

variable "aks_kubernetes_version" {
  description = "Desired Kubernetes version for the AKS cluster."
  type        = string
  default     = "1.27.9" # Specify a recent, supported version. Check Azure for latest patch.
}

variable "aks_default_node_pool_name" {
  description = "Name for the default node pool in AKS."
  type        = string
  default     = "agentpool" # Standard default name
}

variable "aks_default_node_count" {
  description = "Initial number of nodes in the default node pool."
  type        = number
  default     = 2
}

variable "aks_default_node_vm_size" {
  description = "VM size for the nodes in the default node pool (e.g., Standard_DS2_v2)."
  type        = string
  default     = "Standard_DS2_v2"
}

variable "aks_subnet_id" {
  description = "The ID of the subnet where AKS nodes will be deployed (output from vnet.tf)."
  type        = string
  # Example: azurerm_subnet.aks.id
}

variable "log_analytics_workspace_id" {
  description = "The resource ID of the Log Analytics workspace for Azure Monitor for containers. Optional."
  type        = string
  default     = null # If null, Azure Monitor integration will be disabled or use default workspace.
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

# --- Azure Kubernetes Service (AKS) Cluster ---
resource "azurerm_kubernetes_cluster" "main" {
  name                = var.aks_cluster_name
  location            = var.azure_region
  resource_group_name = var.resource_group_name
  dns_prefix          = "${var.project_name}-aks-${lower(replace(var.azure_region, " ", ""))}" # Needs to be globally unique in Azure DNS if public
  kubernetes_version  = var.aks_kubernetes_version

  default_node_pool {
    name                = var.aks_default_node_pool_name
    node_count          = var.aks_default_node_count
    vm_size             = var.aks_default_node_vm_size
    vnet_subnet_id      = var.aks_subnet_id # Associate with the dedicated AKS subnet
    # enable_auto_scaling = true
    # min_count           = 1
    # max_count           = 3
    # type                = "VirtualMachineScaleSets" # Default
    tags = {
      "nodepool-type" = "system" # Or "user" for additional node pools
      "environment"   = "Production" # Or as per your tagging strategy
      "project"       = var.project_name
    }
  }

  identity {
    type = "SystemAssigned" # Use SystemAssigned MI for the cluster control plane for simplicity.
                            # UserAssigned MI can be used for more granular control plane identity.
                            # Kubelet identity (for node permissions) is separate and can be UserAssigned MI.
                            # This is set up in managed_identity.tf and associated with node pools there or via default_node_pool.
  }

  network_profile {
    network_plugin     = "azure"  # Use Azure CNI for better performance and VNet integration
    network_policy     = "azure"  # Use Azure Network Policy (requires Azure CNI). Alternatively "calico".
    service_cidr       = var.aks_service_cidr
    dns_service_ip     = var.aks_dns_service_ip
    docker_bridge_cidr = var.aks_docker_bridge_cidr
    # load_balancer_sku = "Standard" # Default is Standard
  }

  # Azure Monitor for containers integration
  oms_agent {
    log_analytics_workspace_id = var.log_analytics_workspace_id
    # msi_auth_for_monitoring_enabled = true # Use Managed Identity for OMS agent authentication (recommended)
                                         # This would typically use the Kubelet identity or a dedicated one.
  }

  # Addon Profiles
  addon_profile {
    # Azure Key Vault Provider for Secrets Store CSI Driver
    # This addon is crucial for integrating Azure Key Vault with Kubernetes for secret management.
    azure_keyvault_secrets_provider {
      enabled = true
      # secret_rotation_enabled  = true # Optional: enable automatic rotation of secrets
      # secret_rotation_interval = "2m"  # Optional: rotation interval
    }

    # Other common addons (examples, can be enabled as needed)
    # http_application_routing { # For easy Ingress setup with nip.io DNS - good for dev/test
    #   enabled = false
    # }
    # azure_policy {
    #   enabled = false
    # }
    # ingress_application_gateway { # If using AGIC
    #   enabled = false
    # }
  }

  # --- Optional: Azure AD Integration for Kubernetes RBAC ---
  # azure_active_directory_role_based_access_control {
  #   managed                = true
  #   admin_group_object_ids = ["YOUR_AZURE_AD_ADMIN_GROUP_OBJECT_ID"] # Object ID of an AAD group for cluster admins
  #   # azure_rbac_enabled     = true # For authorizing K8s API access using Azure RBAC roles (distinct from K8s RBAC)
  #   # tenant_id              = "YOUR_AZURE_TENANT_ID"
  # }

  # --- Optional: Private Cluster ---
  # private_cluster_enabled = true
  # To make the API server private. Requires careful network planning (e.g., VNet peering, VPN, ExpressRoute for kubectl access).

  tags = {
    environment = "Production"
    project     = var.project_name
    terraform   = "true"
  }
}

# --- Outputs (typically in outputs.tf) ---
# Moved to outputs.tf as per best practice.
# output "aks_cluster_id" {
#   description = "The ID of the AKS cluster."
#   value       = azurerm_kubernetes_cluster.main.id
# }
#
# output "aks_cluster_name_output" {
#   description = "The name of the AKS cluster."
#   value       = azurerm_kubernetes_cluster.main.name
# }
#
# output "aks_kube_config_raw" {
#   description = "Raw Kubeconfig for the AKS cluster. Sensitive."
#   value       = azurerm_kubernetes_cluster.main.kube_config_raw
#   sensitive   = true
# }
#
# output "aks_kube_config" { # For direct use by Kubernetes provider if needed
#   description = "Kubeconfig block for the AKS cluster."
#   value       = azurerm_kubernetes_cluster.main.kube_config
#   sensitive   = true
# }
#
# output "aks_system_assigned_identity_principal_id" {
#   description = "The Principal ID of the System Assigned Managed Identity for the AKS cluster control plane."
#   value       = azurerm_kubernetes_cluster.main.identity[0].principal_id
# }
#
# output "aks_system_assigned_identity_tenant_id" {
#   description = "The Tenant ID of the System Assigned Managed Identity for the AKS cluster control plane."
#   value       = azurerm_kubernetes_cluster.main.identity[0].tenant_id
# }
#
# output "aks_node_resource_group" {
#   description = "The name of the auto-generated resource group where AKS worker nodes and other resources are deployed (MC_resourcegroupname_clustername_region)."
#   value       = azurerm_kubernetes_cluster.main.node_resource_group
# }
#
# output "aks_oidc_issuer_url" { # For Workload Identity
#   description = "The OIDC issuer URL for the AKS cluster."
#   value       = azurerm_kubernetes_cluster.main.oidc_issuer_url
# }
#
# output "aks_kubelet_identity_object_id" { # If default_node_pool uses SystemAssigned MI by default, or if UserAssigned MI is configured here
#   description = "Object ID of the Kubelet identity (Managed Identity used by nodes)."
#   value       = azurerm_kubernetes_cluster.main.kubelet_identity[0].object_id # Only if default_node_pool creates/uses one with known output here.
                                                                              # This might be better sourced from a separate node pool resource or MI resource.
                                                                              # For `SystemAssigned` on default_node_pool, this value is populated.
                                                                              # If user-assigned MI is used for Kubelet, it's configured elsewhere and its ID passed in.
# }
