# Terraform Version and Backend Configuration

terraform {
  # Specifies the minimum version of Terraform that can be used with this configuration.
  # It's recommended to set this to ensure compatibility with Terraform features and syntax used.
  required_version = ">= 1.3.0" # Example: requires Terraform version 1.3.0 or later

  # --- Terraform Backend Configuration (Azure Blob Storage Example - CRITICAL FOR PRODUCTION) ---
  # The backend configuration defines where Terraform stores its state data.
  # Using a remote backend like Azure Blob Storage is highly recommended for any
  # collaborative or production environment for several reasons:
  #   - State Locking: Prevents multiple users or automation processes from concurrently
  #     modifying the state, which can lead to corruption. Azure Blob Storage uses blob leases for locking.
  #   - Shared State: Allows team members and CI/CD systems to access and modify the same infrastructure state.
  #   - Durability & Availability: Leverages Azure Blob Storage's high durability and availability.
  #   - Versioning: Blob versioning can be enabled on the storage account to keep a history of state changes.
  #   - Security: Access to the state data can be controlled using Azure RBAC and storage account keys/SAS tokens.
  #
  # To use this Azure Blob Storage backend:
  #   1. Create an Azure Storage Account (e.g., "yourprojecttfstate").
  #   2. Create a Blob Container within that storage account (e.g., "terraform-state").
  #   3. Ensure the identity running `terraform init` (user, service principal, or managed identity) has:
  #      - "Storage Blob Data Contributor" or "Storage Blob Data Owner" role on the container (or storage account).
  #      - Permissions to create leases on blobs for state locking.
  #   4. Uncomment the block below and replace the placeholder values with your actual
  #      storage account name, container name, and state file key (path).
  #   5. Run `terraform init`. Terraform will prompt you to copy existing local state (if any)
  #      to the new Azure Blob Storage backend.

  /*
  backend "azurerm" {
    resource_group_name  = "your-terraform-state-rg"       # Name of the resource group containing the storage account
    storage_account_name = "yourtfstatesaccountunique"     # Name of the storage account (globally unique)
    container_name       = "tfstate"                       # Name of the blob container
    key                  = "azure/${var.project_name}/${var.environment_name}/terraform.tfstate" # Path to the state file in the container
    # Optional: Specify if using a specific access key (not recommended for most scenarios, prefer identity-based auth)
    # access_key         = "YOUR_STORAGE_ACCOUNT_ACCESS_KEY"

    # Optional: For using MSI (Managed Service Identity) or Service Principal authentication for the backend
    # use_msi = true # if running Terraform from an Azure resource with MSI
    # subscription_id = "YOUR_AZURE_SUBSCRIPTION_ID"
    # tenant_id       = "YOUR_AZURE_TENANT_ID"
    # client_id       = "YOUR_SP_CLIENT_ID"       # If using Service Principal
    # client_secret   = "YOUR_SP_CLIENT_SECRET"   # If using Service Principal (store securely!)
  }
  */
}

# Note on using variables in backend configuration:
# Variables like var.project_name and var.environment_name in the `key` attribute provide flexibility.
# However, these variables must be available at `terraform init` time. This can be achieved by:
# 1. Using partial configuration: `terraform init -backend-config="key=azure/myproject/dev/terraform.tfstate"`
# 2. Using a backend configuration file: `terraform init -backend-config=backend-dev.conf`
#    (where backend-dev.conf contains the key, storage_account_name, etc.)
# For simpler setups, you might hardcode parts of the key or use a fixed key name per environment.
# Using a consistent naming convention for the state file key is important for organization.
