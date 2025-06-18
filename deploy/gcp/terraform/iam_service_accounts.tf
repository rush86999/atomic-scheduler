# --- Variables ---
variable "gcp_project_id" {
  description = "The GCP project ID where service accounts will be created."
  type        = string
}

variable "project_name" { # General project name for context and naming conventions
  description = "Name of the project, used for display names and descriptions."
  type        = string
}

variable "environment_name" { # For display names and descriptions
  description = "The deployment environment (e.g., dev, staging, prod)."
  type        = string
}

variable "gke_node_sa_account_id_suffix" {
  description = "Suffix for the GKE Node Service Account ID. Full ID e.g. ${var.project_name}-gke-node-${var.gke_node_sa_account_id_suffix}."
  type        = string
  default     = "node-sa" # Example: atomic-gke-node-sa
}

variable "gke_workload_identity_sa_account_id_suffix" {
  description = "Suffix for the GKE Workload Identity Service Account ID. This is the GSA that KSAs will impersonate."
  type        = string
  default     = "wi-sa" # Example: atomic-gke-wi-sa
}

variable "enable_cicd_sa_gcp" {
  description = "Set to true to create a dedicated Service Account for CI/CD."
  type        = bool
  default     = false
}

variable "cicd_sa_account_id_suffix_gcp" {
  description = "Suffix for the CI/CD Service Account ID if enabled."
  type        = string
  default     = "cicd-sa" # Example: atomic-cicd-sa
}

# Placeholder for Kubernetes Service Account (KSA) details for Workload Identity binding
# These would ideally be passed in or configured more dynamically in a production setup.
variable "example_ksa_namespace" {
  description = "Placeholder: Kubernetes namespace of a KSA that will use Workload Identity (e.g., 'kube-system' for CSI driver, or an app namespace)."
  type        = string
  default     = "default" # Replace with actual KSA namespace
}

variable "example_ksa_name" {
  description = "Placeholder: Kubernetes Service Account name that will use Workload Identity (e.g., 'secrets-store-csi-driver-provider-gcp' or an app KSA)."
  type        = string
  default     = "default" # Replace with actual KSA name
}


# --- GKE Node Service Account ---
# This Service Account is used by GKE nodes (Kubelet, etc.) for GCP API access.
# It needs permissions for logging, monitoring, and pulling images from Artifact Registry (assigned in artifact_registry.tf).
locals {
  gke_node_sa_account_id = substr(lower("${var.project_name}-gke-${var.gke_node_sa_account_id_suffix}"), 0, 30) # Max 30 chars, lowercase, etc.
  gke_node_sa_display_name = "GKE Node SA for ${var.project_name} ${var.environment_name}"
}

resource "google_service_account" "gke_node_sa" {
  project      = var.gcp_project_id
  account_id   = local.gke_node_sa_account_id
  display_name = local.gke_node_sa_display_name
  description  = "Service Account for GKE worker nodes in project ${var.project_name}, env ${var.environment_name}."
}

# Assign minimum necessary project-level roles to GKE Node SA
resource "google_project_iam_member" "gke_node_sa_monitoring_writer" {
  project = var.gcp_project_id
  role    = "roles/monitoring.metricWriter"
  member  = "serviceAccount:${google_service_account.gke_node_sa.email}"
}

resource "google_project_iam_member" "gke_node_sa_logging_writer" {
  project = var.gcp_project_id
  role    = "roles/logging.logWriter"
  member  = "serviceAccount:${google_service_account.gke_node_sa.email}"
}
# Note: 'roles/artifactregistry.reader' is assigned to this SA directly on the Artifact Registry repository
# in the artifact_registry.tf file.

# --- GKE Workload Identity Service Account ---
# This is the GCP Service Account (GSA) that Kubernetes Service Accounts (KSAs) will impersonate
# to access GCP services (e.g., Google Secret Manager via Secrets Store CSI Driver).
locals {
  gke_workload_identity_sa_account_id = substr(lower("${var.project_name}-gke-${var.gke_workload_identity_sa_account_id_suffix}"), 0, 30)
  gke_workload_identity_sa_display_name = "GKE Workload Identity SA for ${var.project_name} ${var.environment_name}"
}

resource "google_service_account" "gke_workload_identity_sa" {
  project      = var.gcp_project_id
  account_id   = local.gke_workload_identity_sa_account_id
  display_name = local.gke_workload_identity_sa_display_name
  description  = "GSA for GKE Workload Identity impersonation in project ${var.project_name}, env ${var.environment_name}."
}

# Grant Kubernetes Service Account(s) permission to impersonate this GSA.
# This is the core of Workload Identity: KSA -> GSA.
# The KSA needs to be annotated with `iam.gke.io/gcp-service-account = GSA_EMAIL`.
# The Secrets Store CSI Driver's KSA (often in kube-system) and any application KSAs
# that need to access GCP secrets will need this binding.
resource "google_service_account_iam_member" "gke_workload_identity_user_binding" {
  # This example binds a placeholder KSA. In a real setup, you would:
  # 1. Identify the KSA used by the Secrets Store CSI Driver provider for GCP (e.g., in kube-system).
  # 2. Identify KSAs used by your application pods that will mount secrets.
  # 3. Create multiple `google_service_account_iam_member` resources or use a list with for_each for them.
  # The member format is: "serviceAccount:GCP_PROJECT_ID.svc.id.goog[K8S_NAMESPACE/KSA_NAME]"

  service_account_id = google_service_account.gke_workload_identity_sa.name # Fully qualified name of the GSA
  role               = "roles/iam.workloadIdentityUser"
  member             = "serviceAccount:${var.gcp_project_id}.svc.id.goog[${var.example_ksa_namespace}/${var.example_ksa_name}]"

  # IMPORTANT: Replace var.example_ksa_namespace and var.example_ksa_name with actual values.
  # For the Secrets Store CSI Driver, the KSA is often in the 'kube-system' namespace and might be named
  # 'secrets-store-csi-driver-provider-gcp' or similar, depending on installation method.
  # You will need to create these `google_service_account_iam_member` resources for EACH KSA
  # that needs to impersonate `gke_workload_identity_sa`.
  # Consider using a for_each loop if you have a list of KSAs.
  # Example for a list:
  # variable "workload_identity_ksa_bindings" {
  #   type = map(object({ ksa_namespace = string, ksa_name = string }))
  #   default = {
  #     "csi-driver-provider" = { ksa_namespace = "kube-system", ksa_name = "secrets-store-csi-driver-provider-gcp" },
  #     "my-app-ksa"          = { ksa_namespace = "my-app-ns", ksa_name = "my-app-sa" }
  #   }
  # }
  # for_each = var.workload_identity_ksa_bindings
  # member   = "serviceAccount:${var.gcp_project_id}.svc.id.goog[${each.value.ksa_namespace}/${each.value.ksa_name}]"
}
# Note: The GSA `gke_workload_identity_sa` itself is granted `roles/secretmanager.secretAccessor` on secrets
# in the `secret_manager.tf` file (via `var.gke_secret_accessor_gsa_email_input` which should be this SA's email).


# --- (Optional) CI/CD Service Account ---
locals {
  cicd_sa_account_id = substr(lower("${var.project_name}-${var.cicd_sa_account_id_suffix_gcp}"), 0, 30)
  cicd_sa_display_name = "CI/CD SA for ${var.project_name} ${var.environment_name}"
}

resource "google_service_account" "cicd_sa" {
  count = var.enable_cicd_sa_gcp ? 1 : 0

  project      = var.gcp_project_id
  account_id   = local.cicd_sa_account_id
  display_name = local.cicd_sa_display_name
  description  = "Service Account for CI/CD operations in project ${var.project_name}, env ${var.environment_name}."
}

# Assign roles to CI/CD SA
resource "google_project_iam_member" "cicd_sa_secret_admin" {
  count = var.enable_cicd_sa_gcp ? 1 : 0

  project = var.gcp_project_id
  role    = "roles/secretmanager.admin" # Allows managing secrets (e.g., setting values)
                                        # Or "roles/secretmanager.secretAccessor" if only reading/accessing existing versions.
                                        # "roles/secretmanager.secretVersionManager" to add new versions.
  member  = "serviceAccount:${google_service_account.cicd_sa[0].email}"
}

resource "google_project_iam_member" "cicd_sa_gke_admin" { # Or more granular roles like container.developer
  count = var.enable_cicd_sa_gcp ? 1 : 0

  project = var.gcp_project_id
  role    = "roles/container.admin" # Broad GKE admin role for CI/CD to deploy/manage cluster resources.
                                    # Scope down significantly in production.
  member  = "serviceAccount:${google_service_account.cicd_sa[0].email}"
}
# Note: 'roles/artifactregistry.writer' for CI/CD SA is assigned directly on the Artifact Registry repository
# in the artifact_registry.tf file.


# --- Outputs (typically in outputs.tf) ---
# Moved to outputs.tf as per best practice.
# output "gke_node_service_account_email" {
#   description = "Email of the GKE Node Service Account."
#   value       = google_service_account.gke_node_sa.email
# }
#
# output "gke_workload_identity_service_account_email" {
#   description = "Email of the GSA used for GKE Workload Identity (to be impersonated by KSAs)."
#   value       = google_service_account.gke_workload_identity_sa.email
# }
#
# output "cicd_service_account_email_gcp" {
#   description = "Email of the CI/CD Service Account (if created)."
#   value       = var.enable_cicd_sa_gcp ? google_service_account.cicd_sa[0].email : null
# }
