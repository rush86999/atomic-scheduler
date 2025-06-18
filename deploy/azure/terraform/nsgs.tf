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

variable "aks_subnet_cidr" {
  description = "The CIDR block of the AKS subnet. Used to allow traffic from AKS to other resources like the database."
  type        = string
}

# --- Network Security Group for AKS Subnet ---
resource "azurerm_network_security_group" "aks_subnet_nsg" {
  name                = "${var.project_name}-aks-subnet-nsg"
  location            = var.azure_region
  resource_group_name = var.resource_group_name

  tags = {
    environment = "Production" # Or as per your tagging strategy
    project     = var.project_name
    terraform   = "true"
  }

  # Default outbound rule (Azure allows all outbound by default unless overridden)
  # security_rule {
  #   name                       = "AllowInternetOutbound"
  #   priority                   = 100
  #   direction                  = "Outbound"
  #   access                     = "Allow"
  #   protocol                   = "*"
  #   source_port_range          = "*"
  #   destination_port_range     = "*"
  #   source_address_prefix      = "*" # Or specific to the VNet/Subnet
  #   destination_address_prefix = "Internet"
  # }

  # Allow outbound to Azure Container Registry (if used)
  security_rule {
    name                       = "AllowAcrOutbound"
    priority                   = 110
    direction                  = "Outbound"
    access                     = "Allow"
    protocol                   = "Tcp"
    source_port_range          = "*"
    destination_port_range     = "443" # HTTPS for ACR
    source_address_prefix      = var.aks_subnet_cidr # Or "VirtualNetwork"
    destination_address_prefix = "AzureContainerRegistry" # Service Tag for ACR
  }

  # Allow outbound to Azure Key Vault (if used)
  security_rule {
    name                       = "AllowKeyVaultOutbound"
    priority                   = 120
    direction                  = "Outbound"
    access                     = "Allow"
    protocol                   = "Tcp"
    source_port_range          = "*"
    destination_port_range     = "443" # HTTPS for Key Vault
    source_address_prefix      = var.aks_subnet_cidr # Or "VirtualNetwork"
    destination_address_prefix = "AzureKeyVault" # Service Tag for Key Vault
  }

  # Allow outbound to Azure Monitor (if used by AKS for logging/metrics)
  security_rule {
    name                       = "AllowAzureMonitorOutbound"
    priority                   = 130
    direction                  = "Outbound"
    access                     = "Allow"
    protocol                   = "Tcp" # Primarily TCP, some UDP might be used by agents
    source_port_range          = "*"
    destination_port_ranges    = ["443", "12000"] # Common ports for Azure Monitor
    source_address_prefix      = var.aks_subnet_cidr
    destination_address_prefix = "AzureMonitor" # Service Tag
  }


  # Inbound rules for AKS Subnet
  # Allow inbound traffic from Azure Load Balancer (for services exposed via LoadBalancer type)
  # This is often for HTTP/HTTPS traffic to ingresses or services.
  security_rule {
    name                       = "AllowLoadBalancerInbound"
    priority                   = 200
    direction                  = "Inbound"
    access                     = "Allow"
    protocol                   = "Tcp"
    source_port_range          = "*"
    destination_port_ranges    = ["80", "443"] # Common web ports, adjust as needed
    source_address_prefix      = "AzureLoadBalancer" # Service Tag for Azure Load Balancer
    destination_address_prefix = var.aks_subnet_cidr     # Or "VirtualNetwork"
  }

  # Allow node-to-node communication within the VNet (covers the AKS subnet)
  # AKS nodes often need to communicate with each other for pod networking, kubelet, etc.
  security_rule {
    name                       = "AllowVnetInbound" # Renamed from AllowNodeToNodeInSubnet for clarity
    priority                   = 210
    direction                  = "Inbound"
    access                     = "Allow"
    protocol                   = "*" # Allow all protocols for internal VNet comms
    source_port_range          = "*"
    destination_port_range     = "*"
    source_address_prefix      = "VirtualNetwork" # Service Tag representing the entire VNet
    destination_address_prefix = "VirtualNetwork" # Service Tag
  }

  # Optional: If nodes need to reach Kubernetes API server via its private IP (if VNet integrated and private cluster)
  # security_rule {
  #   name                       = "AllowKubeApiServerInbound" # This rule might be if API server is in a different subnet but same VNet
  #   priority                   = 220
  #   direction                  = "Inbound" # Or Outbound if it's nodes reaching API
  #   access                     = "Allow"
  #   protocol                   = "Tcp"
  #   source_port_range          = "*"
  #   destination_port_range     = "443" # Or the specific private Kube API port
  #   source_address_prefix      = "YOUR_KUBE_API_SERVER_PRIVATE_IP_OR_SUBNET_CIDR" # If applicable
  #   destination_address_prefix = var.aks_subnet_cidr
  # }
  # Note: AKS typically manages rules for control plane communication.
  # If using Azure CNI, AKS might create/manage NSGs on NICs directly.
  # This subnet-level NSG serves as an additional layer or for Kubenet.
}

# --- Network Security Group for Database Subnet (e.g., Azure PostgreSQL) ---
resource "azurerm_network_security_group" "db_subnet_nsg" {
  name                = "${var.project_name}-db-subnet-nsg"
  location            = var.azure_region
  resource_group_name = var.resource_group_name

  tags = {
    environment = "Production"
    project     = var.project_name
    terraform   = "true"
  }

  # Allow inbound PostgreSQL traffic ONLY from the AKS subnet
  security_rule {
    name                       = "AllowPostgresInboundFromAKS"
    priority                   = 100
    direction                  = "Inbound"
    access                     = "Allow"
    protocol                   = "Tcp"
    source_port_range          = "*"
    destination_port_range     = "5432" # PostgreSQL port
    source_address_prefix      = var.aks_subnet_cidr # CIDR of the AKS subnet
    destination_address_prefix = "*" # Or the specific CIDR of the DB subnet
  }

  # Deny all other inbound traffic (Azure NSGs have a default DenyAllInBound rule at priority 65500 if no other rules match)
  # No explicit deny rule needed here unless overriding default behavior or needing higher priority deny.

  # Allow outbound traffic from DB subnet (as needed by the database)
  # Example: Allow outbound to Azure Storage for backups (if Azure PostgreSQL is configured for this)
  security_rule {
    name                       = "AllowDbOutboundToAzureStorage"
    priority                   = 100
    direction                  = "Outbound"
    access                     = "Allow"
    protocol                   = "Tcp"
    source_port_range          = "*"
    destination_port_range     = "443" # HTTPS for Azure Storage
    source_address_prefix      = "*"   # Or specific DB subnet CIDR
    destination_address_prefix = "Storage" # Service Tag for Azure Storage
  }

  # Example: Allow outbound for DNS resolution if not using default Azure DNS
  # security_rule {
  #   name                       = "AllowDnsOutbound"
  #   priority                   = 110
  #   direction                  = "Outbound"
  #   access                     = "Allow"
  #   protocol                   = "Udp"
  #   source_port_range          = "*"
  #   destination_port_range     = "53"
  #   source_address_prefix      = "*"
  #   destination_address_prefix = "YOUR_DNS_SERVER_IPS" # e.g., "168.63.129.16" for Azure DNS
  # }

  # Allow outbound to Azure Monitor (if DB sends logs/metrics)
  security_rule {
    name                       = "AllowDbOutboundToAzureMonitor"
    priority                   = 120
    direction                  = "Outbound"
    access                     = "Allow"
    protocol                   = "Tcp"
    source_port_range          = "*"
    destination_port_ranges    = ["443", "12000"]
    source_address_prefix      = "*" # Or specific DB subnet CIDR
    destination_address_prefix = "AzureMonitor"
  }
}

# Outputs (optional, but useful)
output "aks_subnet_nsg_id" {
  description = "The ID of the AKS Subnet Network Security Group."
  value       = azurerm_network_security_group.aks_subnet_nsg.id
}

output "db_subnet_nsg_id" {
  description = "The ID of the Database Subnet Network Security Group."
  value       = azurerm_network_security_group.db_subnet_nsg.id
}
