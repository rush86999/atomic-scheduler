# Terraform Version and Backend Configuration

terraform {
  # Specifies the minimum version of Terraform that can be used with this configuration.
  # It's recommended to set this to ensure compatibility with Terraform features and syntax used.
  required_version = ">= 1.3.0" # Example: requires Terraform version 1.3.0 or later

  # --- Terraform Backend Configuration (Google Cloud Storage Example - CRITICAL FOR PRODUCTION) ---
  # The backend configuration defines where Terraform stores its state data.
  # Using a remote backend like Google Cloud Storage (GCS) is highly recommended for any
  # collaborative or production environment for several reasons:
  #   - State Locking: GCS backend supports state locking to prevent multiple users or automation
  #     processes from concurrently modifying the state, which can lead to corruption.
  #     (GCS uses an object with the name of the state file + ".tflock" for locking).
  #   - Shared State: Allows team members and CI/CD systems to access and modify the same infrastructure state.
  #   - Durability & Availability: Leverages GCS's high durability and availability.
  #   - Versioning: GCS bucket object versioning can be enabled to keep a history of state changes.
  #   - Security: Access to the state data can be controlled using IAM permissions on the GCS bucket.
  #
  # To use this GCS backend:
  #   1. Create a GCS bucket in your GCP project (e.g., "yourproject-tfstate-bucket").
  #      Ensure the bucket has versioning enabled: `gsutil versioning set on gs://YOUR_BUCKET_NAME`
  #   2. Ensure the identity running `terraform init` (user, service account) has:
  #      - `roles/storage.objectAdmin` on the bucket, or at least permissions to read/write/delete objects
  #        (e.g., `storage.objects.create`, `storage.objects.get`, `storage.objects.delete`, `storage.objects.list`).
  #   3. Uncomment the block below and replace the placeholder values with your actual
  #      bucket name and desired prefix (path) for the state file.
  #   4. Run `terraform init`. Terraform will prompt you to copy existing local state (if any)
  #      to the new GCS backend.

  /*
  backend "gcs" {
    bucket  = "your-gcp-project-terraform-state-bucket" # Replace with your GCS bucket name (must be globally unique)
    prefix  = "gcp/${var.project_name}/${var.environment_name}/terraform.tfstate" # Path within the bucket to store the state file
    # project = "YOUR_GCP_PROJECT_ID" # Optional: Defaults to the project configured in the provider, but can be specified if state bucket is in a different project.

    # Optional: Credentials file for accessing the backend bucket (if not using Application Default Credentials)
    # credentials = "/path/to/your/gcs-backend-sa-key.json"

    # Optional: Impersonate a service account for backend operations
    # impersonate_service_account = "terraform-backend-sa@YOUR_GCP_PROJECT_ID.iam.gserviceaccount.com"
  }
  */
}

# Note on using variables in backend configuration:
# Variables like var.project_name and var.environment_name in the `prefix` attribute provide flexibility.
# However, these variables must be available at `terraform init` time. This can be achieved by:
# 1. Using partial configuration: `terraform init -backend-config="prefix=gcp/myproject/dev/terraform.tfstate"`
# 2. Using a backend configuration file: `terraform init -backend-config=backend-dev.conf`
#    (where backend-dev.conf contains the prefix, bucket, etc.)
# For simpler setups, you might hardcode parts of the prefix or use a fixed prefix name per environment.
# Using a consistent naming convention for the state file prefix is important for organization.
