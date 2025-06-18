# --- Variables ---
variable "gcp_project_id" {
  description = "The GCP project ID where the GKE cluster will be deployed."
  type        = string
}

variable "gcp_region" {
  description = "The GCP region for the GKE cluster."
  type        = string
}

variable "gcp_zone" {
  description = "The GCP zone for a zonal GKE cluster. If null or empty, a regional cluster will be created in var.gcp_region."
  type        = string
  default     = null # Set to a specific zone like "us-central1-a" for a zonal cluster
}

variable "project_name" {
  description = "Name of the project, used for tagging and naming resources."
  type        = string
}

variable "gke_cluster_name_suffix" {
  description = "Suffix for the GKE cluster name. Full name will be e.g. ${var.project_name}-gke-${var.gke_cluster_name_suffix}-${var.environment_name}."
  type        = string
  default     = "cluster"
}

variable "gke_kubernetes_version" {
  description = "Desired Kubernetes version for the GKE cluster. Can be a specific version like '1.27.9-gke.100' or a release channel."
  type        = string
  default     = "1.27" # Or use a release channel, e.g., "regular"
}

# Default Node Pool Variables (for the primary node pool we create)
variable "gke_primary_node_pool_name" {
  description = "Name for the primary GKE node pool."
  type        = string
  default     = "primary-nodes"
}

variable "gke_primary_node_initial_count" { # Renamed from gke_default_node_count for clarity
  description = "Initial number of nodes per zone in the primary node pool. If regional, this is per-zone."
  type        = number
  default     = 1 # For a small dev cluster; production might start with 2 or 3 per zone.
}

variable "gke_primary_node_machine_type" { # Renamed from gke_default_node_machine_type
  description = "Machine type for the nodes in the primary GKE node pool."
  type        = string
  default     = "e2-medium" # General-purpose machine type
}

# Network and Subnetwork names (outputs from vpc_network.tf)
variable "vpc_network_name_from_output" {
  description = "The name of the VPC network to deploy GKE into."
  type        = string
  # Example: google_compute_network.main.name
}

variable "gke_subnet_name_from_output" {
  description = "The name of the GKE subnetwork to deploy GKE into."
  type        = string
  # Example: google_compute_subnetwork.gke_subnet.name
}

# Secondary IP Range names for Pods and Services (must match names in vpc_network.tf)
variable "gke_pods_cidr_name_from_output" {
  description = "The name of the secondary IP range for GKE Pods."
  type        = string
  # Example: from var.gke_pods_cidr_name or direct output of the range name
}

variable "gke_services_cidr_name_from_output" {
  description = "The name of the secondary IP range for GKE Services."
  type        = string
  # Example: from var.gke_services_cidr_name or direct output of the range name
}

variable "gke_node_service_account_email" {
  description = "The email of the GCP Service Account to be used by GKE nodes. If empty, uses Compute Engine default SA."
  type        = string
  default     = "default" # Uses the Compute Engine default service account.
                          # This should be updated to use a dedicated, least-privilege SA created in iam_service_accounts.tf.
}

# --- GKE Cluster ---
# Using a local to determine if the cluster is regional or zonal based on var.gcp_zone
locals {
  is_regional_cluster = var.gcp_zone == null || var.gcp_zone == ""
  cluster_location    = local.is_regional_cluster ? var.gcp_region : var.gcp_zone
  final_gke_cluster_name = lower("${var.project_name}-gke-${var.gke_cluster_name_suffix}-${var.environment_name}") # Example construction
}

resource "google_container_cluster" "primary" {
  project  = var.gcp_project_id
  name     = local.final_gke_cluster_name
  location = local.cluster_location # Can be region or zone

  # Networking
  network    = var.vpc_network_name_from_output    # Reference to the VPC network name
  subnetwork = var.gke_subnet_name_from_output # Reference to the GKE subnetwork name

  ip_allocation_policy {
    cluster_secondary_range_name  = var.gke_pods_cidr_name_from_output     # For Pods
    services_secondary_range_name = var.gke_services_cidr_name_from_output # For Services
  }

  # We create our own primary node pool, so remove the default one.
  initial_node_count       = 1
  remove_default_node_pool = true

  # Kubernetes versioning - use a specific version or a release channel
  # min_master_version = var.gke_kubernetes_version # For specific version control if not using release_channel
  release_channel {
    channel = "REGULAR" # Options: RAPID, REGULAR, STABLE. REGULAR is a good balance.
                        # If using release_channel, min_master_version and kubernetes_version might be ignored or conflict.
                        # For this example, assuming release_channel is preferred.
                        # If specific version is needed, comment out release_channel and uncomment min_master_version.
  }
  # kubernetes_version = var.gke_kubernetes_version # Can be set with release_channel too for initial version if channel supports it

  # Enable Workload Identity (recommended for secure access to GCP services from pods)
  workload_identity_config {
    workload_pool = "${var.gcp_project_id}.svc.id.goog" # Default workload identity pool for the project
  }

  # Addons Configuration
  addons_config {
    # Enable Network Policy (e.g., for Calico or other network policy enforcements)
    network_policy_config {
      disabled = false # false means enabled.
    }
    # GCE Persistent Disk CSI Driver (recommended for stateful workloads)
    gce_persistent_disk_csi_driver_config {
      enabled = true
    }
    # Kubernetes Dashboard (generally recommended to keep disabled for security, access via kubectl proxy if needed)
    # kubernetes_dashboard {
    #   disabled = true
    # }
    # Config Connector (for managing GCP resources via K8s anifests, optional)
    # config_connector_config {
    #   enabled = false
    # }
    # Horizontal Pod Autoscaling
    horizontal_pod_autoscaling {
      disabled = false
    }
  }

  # Logging and Monitoring (using Google Cloud Operations suite)
  logging_service    = "logging.googleapis.com/kubernetes" # GKE standard logging
  monitoring_service = "monitoring.googleapis.com/kubernetes" # GKE standard monitoring

  # Security settings (examples)
  # private_cluster_config { # For private clusters
  #   enable_private_nodes    = true
  #   enable_private_endpoint = true # Master API endpoint is internal
  #   master_ipv4_cidr_block  = "172.16.0.32/28" # Example private CIDR for master
  # }
  # database_encryption {
  #   state    = "DECRYPTED" # Or "ENCRYPTED" with a key_name for Application-layer Secret Encryption
  #   key_name = "YOUR_KMS_KEY_FOR_GKE_SECRETS" # If using ENCRYPTED
  # }
  # enable_shielded_nodes = true # For enhanced node security

  # Maintenance policy (example)
  # maintenance_policy {
  #   recurring_window {
  #     start_time = "2023-10-26T03:00:00Z" # RFC3339 format
  #     end_time   = "2023-10-26T07:00:00Z"
  #     recurrence = "FREQ=WEEKLY;BYDAY=SA" # Every Saturday
  #   }
  # }

  # Network policy is enabled via addons_config.network_policy_config
  # network_policy {
  #   enabled  = true
  #   provider = "CALICO" # Or "AZURE" if this were AKS. For GKE, it's just enabling network_policy_config.
  # }

  # Default labels for the cluster itself
  # resource_labels = {
  #   project     = var.project_name
  #   environment = var.environment_name
  #   terraform   = "true"
  # }
  # Note: GKE applies its own labels. Tags are not directly supported on google_container_cluster.
}

# --- Primary GKE Node Pool ---
resource "google_container_node_pool" "primary_nodes" {
  project    = var.gcp_project_id
  name       = var.gke_primary_node_pool_name
  location   = local.cluster_location # Must match cluster location (region or zone)
  cluster    = google_container_cluster.primary.name
  node_count = var.gke_primary_node_initial_count # Number of nodes per zone if regional, total if zonal.

  # If the cluster is regional, node_locations should specify zones within the region.
  # If not specified for a regional node pool, GKE will pick zones.
  # node_locations = local.is_regional_cluster ? ["${var.gcp_region}-a", "${var.gcp_region}-b", "${var.gcp_region}-c"] : null # Example for 3 zones

  node_config {
    machine_type = var.gke_primary_node_machine_type
    # Service Account for nodes.
    # Using "default" uses the Compute Engine default service account.
    # For production, create a dedicated, least-privilege service account for nodes
    # and provide its email here (e.g., from iam_service_accounts.tf).
    service_account = var.gke_node_service_account_email

    oauth_scopes = [
      "https://www.googleapis.com/auth/devstorage.read_only", # For GCR/GAR image pulls
      "https://www.googleapis.com/auth/logging.write",       # For Cloud Logging
      "https://www.googleapis.com/auth/monitoring",          # For Cloud Monitoring
      "https://www.googleapis.com/auth/servicecontrol",
      "https://www.googleapis.com/auth/service.management.readonly",
      "https://www.googleapis.com/auth/trace.append"
    ]

    # Tags for firewall rules and organization
    tags = ["gke-node", "${local.final_gke_cluster_name}-node", var.project_name]

    # Labels for Kubernetes node objects
    # labels = {
    #   "node-pool-type" = "primary"
    # }

    # Optional: Preemptible VMs for cost savings on stateless/batch workloads
    # preemptible  = true

    # Optional: Shielded GKE Nodes (enhances security)
    # shielded_instance_config {
    #   enable_secure_boot          = true
    #   enable_integrity_monitoring = true
    # }
  }

  # Node pool management (auto-repair and auto-upgrade are generally recommended)
  # management {
  #   auto_repair  = true
  #   auto_upgrade = true
  # }

  # Autoscaling for the node pool (optional)
  # autoscaling {
  #   min_node_count = 1
  #   max_node_count = 5
  # }

  # Upgrade settings (example: Max surge and max unavailable during upgrades)
  # upgrade_settings {
  #   max_surge       = 1
  #   max_unavailable = 0 # Or 1 if max_surge is also 1 for rolling updates
  # }
}

# --- Outputs (typically in outputs.tf) ---
# Moved to outputs.tf as per best practice.
# output "gke_cluster_name_output" {
#   description = "The name of the GKE cluster."
#   value       = google_container_cluster.primary.name
# }
#
# output "gke_cluster_endpoint" {
#   description = "The IP address of the GKE cluster master endpoint."
#   value       = google_container_cluster.primary.endpoint
#   sensitive   = true # Endpoint might be considered sensitive depending on network setup
# }
#
# output "gke_cluster_ca_certificate" {
#   description = "The GKE cluster master CA certificate (base64 encoded)."
#   value       = google_container_cluster.primary.master_auth[0].cluster_ca_certificate
#   sensitive   = true
# }
#
# output "gke_workload_identity_pool" {
#   description = "The Workload Identity Pool for the GKE cluster."
#   value       = "${var.gcp_project_id}.svc.id.goog" # Standard format
# }
#
# output "gke_primary_node_pool_name_output" {
#   description = "The name of the primary GKE node pool."
#   value       = google_container_node_pool.primary_nodes.name
# }
#
# output "gke_location" {
#   description = "The location (region or zone) of the GKE cluster."
#   value       = google_container_cluster.primary.location
# }
