# Terraform Version and Backend Configuration

# This block specifies the version of Terraform that this configuration is compatible with.
# It's good practice to set a minimum required version to ensure compatibility
# with features and syntax used in the configuration.
terraform {
  required_version = ">= 1.3.0" # Example: requires Terraform version 1.3.0 or later

  # --- Terraform Backend Configuration (S3 Example - CRITICAL FOR PRODUCTION) ---
  # The backend configuration tells Terraform where to store its state files.
  # Using a remote backend like AWS S3 is highly recommended for any collaborative or production environment.
  # It provides:
  #   - State Locking: Prevents concurrent modifications to the state, avoiding corruption.
  #   - Shared State: Allows multiple team members to work on the same infrastructure.
  #   - Versioning: S3 bucket versioning can keep a history of state file changes.
  #   - Security: IAM policies can control access to the state file.
  #
  # To use this S3 backend:
  #   1. Create an S3 bucket in your AWS account (e.g., "myproject-terraform-state-bucket").
  #      Ensure the bucket has versioning enabled and appropriate access controls.
  #   2. Create a DynamoDB table (e.g., "myproject-terraform-state-lock") for state locking.
  #      The table must have a primary key named "LockID" (Type: String).
  #   3. Uncomment the block below and replace the placeholder values with your actual bucket name,
  #      key path, region, and DynamoDB table name.
  #   4. Run `terraform init` to initialize the backend. Terraform will prompt you to copy
  #      existing local state (if any) to the new S3 backend.

  /*
  backend "s3" {
    bucket         = "your-terraform-state-bucket-name" # Replace with your S3 bucket name
    key            = "aws/${var.project_name}/${var.environment_name}/terraform.tfstate" # Path within the bucket to store the state file
    region         = "us-east-1"                        # Replace with the AWS region of your S3 bucket and DynamoDB table
    encrypt        = true                               # Enable server-side encryption for the state file
    dynamodb_table = "your-terraform-state-lock-table"  # Replace with your DynamoDB table name for state locking

    # Optional: Specify an AWS profile if needed for accessing the backend S3 bucket and DynamoDB table
    # profile = "your-aws-backend-profile"

    # Optional: Specify tags for the state file object in S3
    # workspace_key_prefix = "env" # Organizes state by workspace under the main key if using Terraform Cloud/Workspaces
  }
  */
}

# Note: The variables var.project_name and var.environment_name used in the example backend key
# would need to be available at `terraform init` time. This can be achieved by:
# 1. Defining them in a .tfvars file and using `terraform init -var-file=myvars.tfvars`.
# 2. Using partial configuration with `terraform init -backend-config="key=..."`.
# 3. For simpler setups, you might hardcode parts of the key or use a fixed key name per environment.
# Using variables in the backend key provides flexibility but requires careful initialization.
# Alternatively, a common pattern is to have separate backend configuration files per environment
# and initialize with `terraform init -backend-config=backend-dev.conf`, etc.
