# --- Variables ---
variable "azure_region" {
  description = "The Azure region where the VNet and subnets will be created."
  type        = string
}

variable "resource_group_name" {
  description = "The name of the Azure Resource Group where resources will be deployed."
  type        = string
}

variable "project_name" {
  description = "Name of the project, used for tagging and naming resources."
  type        = string
}

variable "vnet_cidr" {
  description = "The CIDR block for the Azure Virtual Network."
  type        = string
  default     = "10.1.0.0/16" # Example CIDR for the VNet
}

variable "aks_subnet_cidr" {
  description = "The CIDR block for the AKS subnet."
  type        = string
  default     = "10.1.1.0/24" # Example CIDR for AKS subnet
}

variable "application_subnet_cidr" {
  description = "The CIDR block for the general application subnet."
  type        = string
  default     = "10.1.2.0/24" # Example CIDR for application subnet
}

variable "database_subnet_cidr" {
  description = "The CIDR block for the database subnet."
  type        = string
  default     = "10.1.3.0/24" # Example CIDR for database subnet
}

# --- Virtual Network (VNet) ---
resource "azurerm_virtual_network" "main" {
  name                = "${var.project_name}-vnet"
  address_space       = [var.vnet_cidr]
  location            = var.azure_region
  resource_group_name = var.resource_group_name

  tags = {
    environment = "Production" # Or as per your tagging strategy
    project     = var.project_name
    terraform   = "true"
  }
}

# --- Subnets ---

# AKS Subnet
resource "azurerm_subnet" "aks" {
  name                 = "${var.project_name}-aks-subnet"
  resource_group_name  = var.resource_group_name
  virtual_network_name = azurerm_virtual_network.main.name
  address_prefixes     = [var.aks_subnet_cidr]

  # No specific service endpoints or delegations needed for a basic AKS subnet usually,
  # unless specific integrations require them (e.g., Azure Container Instances).
  # AKS itself will configure network properties as needed, especially with Azure CNI.
  # If using Azure CNI, AKS might also create/manage an NSG on the NICs in this subnet.
  # An NSG can be associated with this subnet via `azurerm_subnet_network_security_group_association`.

  tags = {
    Name      = "${var.project_name}-aks-subnet"
    Purpose   = "AKS Nodes and Pods"
    Project   = var.project_name
    Terraform = "true"
  }
}

# Application Subnet (for other VMs, App Service Environment, etc. - if needed)
# For this project, most applications run within AKS.
# This subnet is included for completeness if other non-AKS apps or services need VNet integration.
resource "azurerm_subnet" "application" {
  name                 = "${var.project_name}-app-subnet"
  resource_group_name  = var.resource_group_name
  virtual_network_name = azurerm_virtual_network.main.name
  address_prefixes     = [var.application_subnet_cidr]

  tags = {
    Name      = "${var.project_name}-app-subnet"
    Purpose   = "General Application Services"
    Project   = var.project_name
    Terraform = "true"
  }
}

# Database Subnet (for Azure Database for PostgreSQL, SQL Managed Instance, etc.)
resource "azurerm_subnet" "database" {
  name                 = "${var.project_name}-db-subnet"
  resource_group_name  = var.resource_group_name
  virtual_network_name = azurerm_virtual_network.main.name
  address_prefixes     = [var.database_subnet_cidr]

  # Service Endpoints allow PaaS services like Azure SQL, Azure Storage to be secured to your VNet.
  # "Microsoft.Sql" is for Azure SQL Database, SQL Managed Instance, and Azure Database for PostgreSQL (server, not flexible server usually).
  # For Azure Database for PostgreSQL Flexible Server, Private Endpoints are more common.
  service_endpoints = ["Microsoft.Sql"] # Enables direct VNet integration for supported SQL services.

  # If using Private Endpoints for Azure Database for PostgreSQL Flexible Server (recommended),
  # you might need to disable network policies on the subnet for the private endpoint.
  # This is typically done on the subnet where the *private endpoint* is created, which might be this DB subnet or another.
  # private_endpoint_network_policies_enabled = false # Set to false if Private Endpoints will be deployed into this subnet.
                                                    # Default is true. Check Azure provider docs for exact resource.
                                                    # This is on `azurerm_subnet.private_endpoint_subnet_enforce_private_link_endpoint_network_policies`
                                                    # or `azurerm_subnet.private_endpoint_subnet_enforce_private_link_service_network_policies`

  # Delegations (e.g., for Azure Database for PostgreSQL Flexible Server if deployed directly into a delegated subnet)
  # delegation {
  #   name = "Microsoft.DBforPostgreSQL.flexibleServers"
  #   service_delegation {
  #     name    = "Microsoft.DBforPostgreSQL/flexibleServers"
  #     actions = ["Microsoft.Network/virtualNetworks/subnets/join/action"]
  #   }
  # }

  tags = {
    Name      = "${var.project_name}-db-subnet"
    Purpose   = "Database Services"
    Project   = var.project_name
    Terraform = "true"
  }
}

# --- (Optional) Route Table Example ---
# By default, subnets use the system route table for the VNet, which allows communication
# between all subnets in the VNet and uses default Azure routing for outbound traffic.
# If custom routing is needed (e.g., to a Network Virtual Appliance (NVA) for internet traffic,
# or specific routes to on-premises networks via VPN/ExpressRoute), you would define a route table
# and associate it with subnets.

/*
resource "azurerm_route_table" "main_rt" {
  name                          = "${var.project_name}-main-rt"
  location                      = var.azure_region
  resource_group_name           = var.resource_group_name
  disable_bgp_route_propagation = false # Set to true if using BGP with ExpressRoute/VPN and want to control routes manually

  route {
    name           = "DefaultToInternetViaNVA" # Example route
    address_prefix = "0.0.0.0/0"
    next_hop_type  = "VirtualAppliance"
    next_hop_in_ip_address = "IP_ADDRESS_OF_YOUR_NVA" # IP address of your firewall/NVA
  }

  tags = {
    Name      = "${var.project_name}-main-rt"
    Project   = var.project_name
    Terraform = "true"
  }
}

# Example association (e.g., for the application subnet)
resource "azurerm_subnet_route_table_association" "application_subnet_rt_assoc" {
  subnet_id      = azurerm_subnet.application.id
  route_table_id = azurerm_route_table.main_rt.id
}
*/

# --- Outputs (typically in outputs.tf) ---
# Moved to outputs.tf as per best practice.
# output "vnet_id" {
#   description = "The ID of the created Azure Virtual Network."
#   value       = azurerm_virtual_network.main.id
# }
#
# output "vnet_name" {
#   description = "The name of the created Azure Virtual Network."
#   value       = azurerm_virtual_network.main.name
# }
#
# output "aks_subnet_id" {
#   description = "The ID of the AKS subnet."
#   value       = azurerm_subnet.aks.id
# }
#
# output "application_subnet_id" {
#   description = "The ID of the application subnet."
#   value       = azurerm_subnet.application.id
# }
#
# output "database_subnet_id" {
#   description = "The ID of the database subnet."
#   value       = azurerm_subnet.database.id
# }
#
# output "database_subnet_address_prefix" { # Useful for NSG rules on other subnets
#   description = "The address prefix of the database subnet."
#   value       = azurerm_subnet.database.address_prefixes[0]
# }
#
# output "aks_subnet_address_prefix" { # Useful for NSG rules on other subnets
#   description = "The address prefix of the AKS subnet."
#   value       = azurerm_subnet.aks.address_prefixes[0]
# }
