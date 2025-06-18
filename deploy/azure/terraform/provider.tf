# Terraform Azure Provider Configuration

terraform {
  required_providers {
    azurerm = {
      source  = "hashicorp/azurerm"
      version = "~> 3.75" # Specify a compatible version range for the Azure provider
    }
    random = {
      source  = "hashicorp/random"
      version = "~> 3.5" # For generating unique names if needed
    }
    # azuread = { # Uncomment if using the azuread provider for CI/CD SP example in managed_identity.tf
    #   source  = "hashicorp/azuread"
    #   version = "~> 2.40"
    # }
  }
}

# Azure Provider Block
# Authentication:
# The Azure provider supports several ways to authenticate:
# 1. Azure CLI: If you are logged in via `az login`, Terraform will use these credentials. (Common for local dev)
# 2. Service Principal: For CI/CD pipelines, create a Service Principal and set ARM_CLIENT_ID, ARM_CLIENT_SECRET, ARM_SUBSCRIPTION_ID, ARM_TENANT_ID as environment variables.
# 3. Managed Identity: If Terraform is running on an Azure resource with a Managed Identity (e.g., Azure VM, Azure DevOps agent).
provider "azurerm" {
  features {
    # Specific features for Azure resources can be configured here if needed.
    # For example, for Key Vault:
    # key_vault {
    #   purge_soft_delete_on_destroy    = true # Or false depending on desired behavior
    #   recover_soft_deleted_key_vaults = true
    # }
    # resource_group {
    #   prevent_deletion_if_contains_resources = false
    # }
  }

  # Optional: Specify subscription ID if not using the default from Azure CLI context or environment variables.
  # subscription_id = "YOUR_AZURE_SUBSCRIPTION_ID"

  # Optional: Specify tenant ID if not using the default.
  # tenant_id = "YOUR_AZURE_TENANT_ID" # Often taken from azurerm_client_config data source or env var
}

# Variables used by the provider block or for default_tags (if azurerm supported it directly like AWS)
# Azure provider does not have a direct `default_tags` block like AWS.
# Tags are applied per resource or via azurerm_resource_group_template_deployment for RG level tagging.
# We will apply tags directly in each resource definition.
# Variables like region, project_name, environment_name are defined in variables.tf.
