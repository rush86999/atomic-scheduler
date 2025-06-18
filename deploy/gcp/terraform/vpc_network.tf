# --- Variables ---
variable "gcp_project_id" {
  description = "The GCP project ID where the VPC network will be created."
  type        = string
}

variable "gcp_region" {
  description = "The GCP region for the VPC network and related resources."
  type        = string
}

variable "project_name" {
  description = "Name of the project, used for tagging and naming resources."
  type        = string
}

variable "vpc_network_name" {
  description = "The name for the VPC network."
  type        = string
  default     = "atomic-vpc" # Example, can be derived from project_name
}

variable "gke_subnet_name" {
  description = "The name for the GKE subnet."
  type        = string
  default     = "gke-subnet"
}

variable "gke_subnet_cidr" {
  description = "The primary CIDR block for the GKE subnet."
  type        = string
  default     = "10.2.0.0/20" # Example CIDR
}

variable "gke_pods_cidr_name" {
  description = "The name for the GKE Pods secondary IP range."
  type        = string
  default     = "gke-pods-range"
}

variable "gke_pods_cidr_block" {
  description = "The CIDR block for the GKE Pods secondary IP range."
  type        = string
  default     = "10.3.0.0/16" # Example CIDR for Pods
}

variable "gke_services_cidr_name" {
  description = "The name for the GKE Services secondary IP range."
  type        = string
  default     = "gke-services-range"
}

variable "gke_services_cidr_block" {
  description = "The CIDR block for the GKE Services secondary IP range."
  type        = string
  default     = "10.4.0.0/20" # Example CIDR for Services
}

variable "db_subnet_name" {
  description = "The name for the Database subnet (e.g., for Cloud SQL private IP)."
  type        = string
  default     = "db-subnet"
}

variable "db_subnet_cidr" {
  description = "The CIDR block for the Database subnet."
  type        = string
  default     = "10.5.0.0/24" # Example CIDR for DB subnet
}

variable "vpc_network_cidr_block_for_firewall" {
  description = "A representative CIDR block for the entire VPC network (e.g., the VPC's primary range or a supernet) to be used in firewall rules for internal traffic. Should encompass primary ranges of subnets within the VPC."
  type        = string
  default     = "10.0.0.0/8" # Example, adjust to match your actual VPC address space planning if more restrictive.
                             # This is used as source for allow-internal rule.
}

# --- VPC Network ---
resource "google_compute_network" "main" {
  project                 = var.gcp_project_id
  name                    = var.vpc_network_name
  auto_create_subnetworks = false                         # Custom mode VPC
  routing_mode            = "REGIONAL"                    # Regional dynamic routing
  mtu                     = 1460                          # Default MTU

  description = "Main VPC network for the ${var.project_name} project."
}

# --- GKE Subnetwork ---
resource "google_compute_subnetwork" "gke_subnet" {
  project                  = var.gcp_project_id
  name                     = var.gke_subnet_name
  ip_cidr_range            = var.gke_subnet_cidr
  network                  = google_compute_network.main.self_link
  region                   = var.gcp_region
  private_ip_google_access = true # Allows VMs in this subnet to reach Google APIs without external IPs

  secondary_ip_range {
    range_name    = var.gke_pods_cidr_name
    ip_cidr_range = var.gke_pods_cidr_block
  }

  secondary_ip_range {
    range_name    = var.gke_services_cidr_name
    ip_cidr_range = var.gke_services_cidr_block
  }

  log_config {
    aggregation_interval = "INTERVAL_10_MIN"
    flow_sampling        = 0.5
    metadata             = "INCLUDE_ALL_METADATA"
  }

  description = "Subnetwork for GKE cluster nodes, pods, and services."
}

# --- Database Subnetwork ---
# For services like Cloud SQL private IP or other managed database services.
resource "google_compute_subnetwork" "db_subnet" {
  project                  = var.gcp_project_id
  name                     = var.db_subnet_name
  ip_cidr_range            = var.db_subnet_cidr
  network                  = google_compute_network.main.self_link
  region                   = var.gcp_region
  private_ip_google_access = true # Often useful for DB instances to access other Google APIs (e.g., for backups to GCS)

  description = "Subnetwork for database services."
}

# --- Cloud NAT for GKE Subnet ---
# Allows GKE nodes and pods without external IPs to access the internet for image pulls, updates, etc.
resource "google_compute_router" "gke_nat_router" {
  project = var.gcp_project_id
  name    = "${var.gke_subnet_name}-nat-router"
  region  = var.gcp_region
  network = google_compute_network.main.id

  description = "Router for Cloud NAT on GKE subnet."
}

resource "google_compute_router_nat" "gke_nat" {
  project                            = var.gcp_project_id
  name                               = "${var.gke_subnet_name}-nat-gateway"
  router                             = google_compute_router.gke_nat_router.name
  region                             = google_compute_router.gke_nat_router.region
  nat_ip_allocate_option             = "AUTO_ONLY"
  source_subnetwork_ip_ranges_to_nat = "LIST_OF_SUBNETWORKS"

  subnetwork {
    name                    = google_compute_subnetwork.gke_subnet.self_link
    source_ip_ranges_to_nat = ["PRIMARY_IP_RANGE", var.gke_pods_cidr_name] # NAT for primary node IPs and Pod IPs
                                                                          # Services IPs are typically not NATted this way as they are for ingress.
  }

  log_config {
    enable = true
    filter = "ERRORS_ONLY" # Or "ALL" for more verbose logging
  }

  # Optional: Configure NAT timeouts if defaults are not suitable
  # udp_idle_timeout_sec                     = 30
  # icmp_idle_timeout_sec                    = 30
  # tcp_established_idle_timeout_sec         = 1200
  # tcp_transitory_idle_timeout_sec          = 30
  # tcp_time_wait_timeout_sec                = 120 # For GKE, consider increasing from default 120 if needed
  # min_ports_per_vm                         = 64  # Default
  # max_ports_per_vm                         = 65536 # Default
  # enable_endpoint_independent_mapping    = true  # Default for new NATs
  # enable_dynamic_port_allocation         = false # Default
}

# --- Firewall Rules ---

# Allow internal traffic within the VPC network.
resource "google_compute_firewall" "allow_internal" {
  project = var.gcp_project_id
  name    = "${var.vpc_network_name}-allow-internal"
  network = google_compute_network.main.self_link

  allow {
    protocol = "tcp"
    ports    = ["0-65535"]
  }
  allow {
    protocol = "udp"
    ports    = ["0-65535"]
  }
  allow {
    protocol = "icmp"
  }

  source_ranges = [var.vpc_network_cidr_block_for_firewall] # Use the broader VPC CIDR or specific subnet CIDRs
  # To be more restrictive, list all subnet CIDRs:
  # [var.gke_subnet_cidr, var.gke_pods_cidr_block, var.gke_services_cidr_block, var.db_subnet_cidr]
  # Using a broader range for simplicity in this base.
  description = "Allow all internal traffic within the VPC network."
}

# Allow SSH to GKE nodes via IAP (Identity-Aware Proxy).
# Nodes must be tagged with "gke-node" (or a custom tag you configure).
resource "google_compute_firewall" "allow_ssh_iap" {
  project = var.gcp_project_id
  name    = "${var.vpc_network_name}-allow-ssh-iap"
  network = google_compute_network.main.self_link

  allow {
    protocol = "tcp"
    ports    = ["22"]
  }

  source_ranges = ["35.235.240.0/20"] # Google's IAP IP range for TCP forwarding
  target_tags   = ["gke-node"]        # Apply to nodes tagged as GKE nodes

  description = "Allow SSH to GKE nodes via IAP."
}

# Allow GKE Control Plane to Nodes (for public clusters, nodes typically initiate this communication).
# This rule is more relevant for private clusters or if specific control plane components need to reach nodes.
# The source range for GKE control plane can vary or be managed by Google.
# For public clusters, usually not strictly needed as nodes reach public endpoint.
# For private clusters, master_ipv4_cidr_block is the source.
# Placeholder rule - actual source might need to be an output from GKE cluster resource if private.
resource "google_compute_firewall" "allow_gke_control_plane_to_nodes" {
  project = var.gcp_project_id
  name    = "${var.vpc_network_name}-allow-gke-cp-to-nodes"
  network = google_compute_network.main.self_link
  priority = 900 # Lower priority than default deny (1000 is default for allow)

  allow {
    protocol = "tcp"
    ports    = ["10250", "443"] # Kubelet API and other control plane communication
  }
  allow {
    protocol = "udp"
    ports    = ["10250"] # Kubelet metrics (if needed)
  }

  # IMPORTANT: The source_ranges for GKE control plane are managed by Google for public clusters.
  # For private clusters, this would be the `private_cluster_config.master_ipv4_cidr_block`.
  # As a placeholder for public clusters where nodes initiate, or if a specific need arises:
  # This rule might not be strictly necessary for public clusters if nodes have outbound internet.
  # Using a tag-based approach if control plane has specific IPs/tags is better than opening broadly.
  # For now, this is a conceptual placeholder. Often, GKE manages its necessary firewall rules.
  # source_ranges = ["GKE_MASTER_IP_RANGE_PLACEHOLDER"] # Replace with actual master IP range if known and needed.
  # For now, let's make it apply to specific GKE node tags, assuming control plane talks to them.
  target_tags = ["gke-node"]
  source_ranges = ["0.0.0.0/0"] # This is too broad, placeholder only.
                                # In a real scenario, this needs to be the GKE control plane's source IPs.
                                # For public GKE, nodes talk to public endpoint, so this inbound rule is less critical.
                                # For private GKE, the master_ipv4_cidr_block output from GKE cluster should be used.
  description = "Placeholder: Allow GKE control plane to nodes (TCP 10250/443). REVIEW AND RESTRICT SOURCE."
}

# Allow health checks from Google Cloud Load Balancers (GCLB) to GKE nodes.
# GCLB health checks come from specific IP ranges.
resource "google_compute_firewall" "allow_gclb_health_checks" {
  project = var.gcp_project_id
  name    = "${var.vpc_network_name}-allow-gclb-hc"
  network = google_compute_network.main.self_link

  allow {
    protocol = "tcp"
    # Ports are usually derived from the BackendService/Ingress configuration.
    # For a general rule, you might allow common HTTP/HTTPS ports or all TCP if backends vary.
    # However, health checks are specific to backend service ports.
    # This rule allows health checks to any port on nodes tagged for GCLB.
    # GKE services of type LoadBalancer will configure specific port checks.
    # This is a broad rule for node-level health checks if GCLB targets instances directly.
    ports = ["0-65535"] # Or specific ports if all your LBs use consistent health check ports.
  }

  source_ranges = [
    "35.191.0.0/16",  # Google Cloud Load Balancer health check range 1
    "130.211.0.0/22"  # Google Cloud Load Balancer health check range 2
  ]
  target_tags = ["gke-node"] # Apply to GKE nodes that might serve GCLB backends

  description = "Allow health checks from Google Cloud Load Balancers to GKE nodes."
}


# --- Outputs (typically in outputs.tf) ---
# Moved to outputs.tf as per best practice.
# output "vpc_network_self_link" {
#   description = "The self_link of the VPC network."
#   value       = google_compute_network.main.self_link
# }
#
# output "vpc_network_name_output" {
#   description = "The name of the VPC network."
#   value       = google_compute_network.main.name
# }
#
# output "gke_subnet_self_link" {
#   description = "The self_link of the GKE subnetwork."
#   value       = google_compute_subnetwork.gke_subnet.self_link
# }
#
# output "gke_subnet_name_output" {
#   description = "The name of the GKE subnetwork."
#   value       = google_compute_subnetwork.gke_subnet.name
# }
#
# output "gke_pods_cidr_name_output" {
#   description = "The name of the GKE Pods secondary IP range."
#   value       = var.gke_pods_cidr_name # Or google_compute_subnetwork.gke_subnet.secondary_ip_range[0].range_name
# }
#
# output "gke_services_cidr_name_output" {
#   description = "The name of the GKE Services secondary IP range."
#   value       = var.gke_services_cidr_name # Or google_compute_subnetwork.gke_subnet.secondary_ip_range[1].range_name
# }
#
# output "db_subnet_self_link" {
#   description = "The self_link of the Database subnetwork."
#   value       = google_compute_subnetwork.db_subnet.self_link
# }
#
# output "db_subnet_name_output" {
#   description = "The name of the Database subnetwork."
#   value       = google_compute_subnetwork.db_subnet.name
# }
