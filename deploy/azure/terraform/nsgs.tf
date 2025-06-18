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

variable "aks_subnet_cidr" { # Used as a source for rules targeting the DB NSG
  description = "The CIDR block of the AKS subnet. Used to allow traffic from AKS to other resources like the database."
  type        = string
  # This would typically be sourced from the vnet.tf output or a common variable if subnets are predefined.
  # Example: default = "10.1.1.0/24"
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

  # --- Outbound Security Rules for AKS Subnet ---
  security_rule {
    name                       = "AllowInternetOutbound"
    priority                   = 100
    direction                  = "Outbound"
    access                     = "Allow"
    protocol                   = "*"
    source_port_range          = "*"
    destination_port_range     = "*"
    source_address_prefix      = var.aks_subnet_cidr # Or "VirtualNetwork" or "*" if less restrictive needed
    destination_address_prefix = "Internet"
  }

  security_rule {
    name                       = "AllowAcrOutbound"
    priority                   = 110
    direction                  = "Outbound"
    access                     = "Allow"
    protocol                   = "Tcp"
    source_port_range          = "*"
    destination_port_range     = "443" # HTTPS for ACR
    source_address_prefix      = var.aks_subnet_cidr
    destination_address_prefix = "AzureContainerRegistry" # Service Tag for ACR
  }

  security_rule {
    name                       = "AllowKeyVaultOutbound"
    priority                   = 120
    direction                  = "Outbound"
    access                     = "Allow"
    protocol                   = "Tcp"
    source_port_range          = "*"
    destination_port_range     = "443" # HTTPS for Key Vault
    source_address_prefix      = var.aks_subnet_cidr
    destination_address_prefix = "AzureKeyVault" # Service Tag for Key Vault
  }

  security_rule {
    name                       = "AllowAzureMonitorOutbound"
    priority                   = 130
    direction                  = "Outbound"
    access                     = "Allow"
    protocol                   = "*" # Azure Monitor uses TCP and UDP
    source_port_range          = "*"
    destination_port_ranges    = ["443", "12000"] # Common ports for Azure Monitor (HTTPS and OMS Gateway)
    source_address_prefix      = var.aks_subnet_cidr
    destination_address_prefix = "AzureMonitor" # Service Tag
  }

  # --- Inbound Security Rules for AKS Subnet ---
  security_rule {
    name                       = "AllowLoadBalancerInbound" # For traffic from Azure LB to nodes/pods
    priority                   = 200
    direction                  = "Inbound"
    access                     = "Allow"
    protocol                   = "Tcp"
    source_port_range          = "*"
    destination_port_ranges    = ["80", "443"] # Common web ports, adjust as needed for your applications
    source_address_prefix      = "AzureLoadBalancer" # Service Tag for Azure Load Balancer
    destination_address_prefix = var.aks_subnet_cidr
  }

  security_rule {
    name                       = "AllowVnetToVnetInbound" # For node-to-node and pod-to-pod within the VNet
    priority                   = 210
    direction                  = "Inbound"
    access                     = "Allow"
    protocol                   = "*" # Allow all protocols for internal VNet communication
    source_port_range          = "*"
    destination_port_range     = "*"
    source_address_prefix      = "VirtualNetwork" # Service Tag representing the entire VNet
    destination_address_prefix = "VirtualNetwork" # Service Tag
  }

  # Note on AKS Control Plane Communication:
  # - If using a public AKS cluster, nodes typically communicate with the control plane over its public endpoint.
  #   Outbound internet access (already allowed) usually covers this.
  # - If using a private AKS cluster, specific rules might be needed if the control plane is in a different subnet
  #   or if UDRs (User Defined Routes) are used. AKS often manages necessary rules for its components.
  # - Azure CNI can also create and manage NSGs directly on node network interfaces, potentially overriding or
  #   supplementing subnet-level NSGs. This NSG acts as a baseline or additional security layer.
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

  # --- Inbound Security Rules for Database Subnet ---
  security_rule {
    name                       = "AllowPostgresInboundFromAKS"
    priority                   = 100
    direction                  = "Inbound"
    access                     = "Allow"
    protocol                   = "Tcp"
    source_port_range          = "*"
    destination_port_range     = "5432" # PostgreSQL port
    source_address_prefix      = var.aks_subnet_cidr # Restrict to AKS subnet CIDR
    destination_address_prefix = "*"                 # Or specific DB subnet CIDR if known and different from source
  }
  # Default DenyAllInBound rule (priority 65500) in Azure NSGs will block other inbound traffic.

  # --- Outbound Security Rules for Database Subnet ---
  security_rule {
    name                       = "AllowDbOutboundToAzureStorage" # For backups, logs, etc.
    priority                   = 100
    direction                  = "Outbound"
    access                     = "Allow"
    protocol                   = "Tcp" # HTTPS for Azure Storage
    source_port_range          = "*"
    destination_port_range     = "443"
    source_address_prefix      = "*" # Or specific DB subnet CIDR
    destination_address_prefix = "Storage" # Service Tag for Azure Storage
  }

  security_rule {
    name                       = "AllowDbOutboundToAzureMonitor" # For DB metrics and logs
    priority                   = 110
    direction                  = "Outbound"
    access                     = "Allow"
    protocol                   = "*" # TCP and UDP
    source_port_range          = "*"
    destination_port_ranges    = ["443", "12000"]
    source_address_prefix      = "*" # Or specific DB subnet CIDR
    destination_address_prefix = "AzureMonitor"
  }

  # Optional: Allow outbound for DNS if not using default Azure DNS and DB needs external resolution for some reason
  # security_rule {
  #   name                       = "AllowDnsOutboundForDb"
  #   priority                   = 120
  #   direction                  = "Outbound"
  #   access                     = "Allow"
  #   protocol                   = "Udp"
  #   source_port_range          = "*"
  #   destination_port_range     = "53"
  #   source_address_prefix      = "*"
  #   destination_address_prefix = "AzureCloud" # Or specific DNS server IPs
  # }
  # Default AllowVnetOutbound and AllowInternetOutbound (if not overridden by a Deny rule) might cover some needs.
  # It's best to be explicit for PaaS database outbound needs.
}

# --- (Optional) Subnet Network Security Group Associations ---
# These resources link the NSGs defined above to specific subnets created in vnet.tf.
# This association can also be done within the azurerm_subnet resource itself using the
# `network_security_group_id` argument, but doing it separately here can be cleaner for some workflows.
# Ensure that the subnet names (`var.aks_subnet_name`, `var.db_subnet_name`) are passed as variables
# or derived correctly if this block is uncommented.

/*
variable "aks_subnet_id" {
  description = "The ID of the AKS subnet to associate with the AKS NSG."
  type        = string
}

variable "db_subnet_id" {
  description = "The ID of the Database subnet to associate with the DB NSG."
  type        = string
}

resource "azurerm_subnet_network_security_group_association" "aks_subnet_nsg_assoc" {
  subnet_id                 = var.aks_subnet_id
  network_security_group_id = azurerm_network_security_group.aks_subnet_nsg.id
}

resource "azurerm_subnet_network_security_group_association" "db_subnet_nsg_assoc" {
  subnet_id                 = var.db_subnet_id
  network_security_group_id = azurerm_network_security_group.db_subnet_nsg.id
}
*/

# --- Outputs (typically in outputs.tf) ---
# Moved to outputs.tf as per best practice.
# output "aks_subnet_nsg_id" {
#   description = "The ID of the AKS Subnet Network Security Group."
#   value       = azurerm_network_security_group.aks_subnet_nsg.id
# }
#
# output "db_subnet_nsg_id" {
#   description = "The ID of the Database Subnet Network Security Group."
#   value       = azurerm_network_security_group.db_subnet_nsg.id
# }
