# --- VPC Network Outputs ---
output "vpc_network_self_link_output" {
  description = "The self_link of the VPC network."
  value       = google_compute_network.main.self_link
}

output "vpc_network_name_output" {
  description = "The name of the VPC network."
  value       = google_compute_network.main.name
}

output "gke_subnet_self_link_output" {
  description = "The self_link of the GKE subnetwork."
  value       = google_compute_subnetwork.gke_subnet.self_link
}

output "gke_subnet_name_output" {
  description = "The name of the GKE subnetwork."
  value       = google_compute_subnetwork.gke_subnet.name
}

output "gke_pods_cidr_name_output" {
  description = "The name of the GKE Pods secondary IP range."
  # This value comes from the variable used to create it, or from the resource attribute if preferred
  value       = google_compute_subnetwork.gke_subnet.secondary_ip_range[0].range_name
}

output "gke_services_cidr_name_output" {
  description = "The name of the GKE Services secondary IP range."
  value       = google_compute_subnetwork.gke_subnet.secondary_ip_range[1].range_name
}

output "db_subnet_self_link_output" {
  description = "The self_link of the Database subnetwork."
  value       = google_compute_subnetwork.db_subnet.self_link
}

output "db_subnet_name_output" {
  description = "The name of the Database subnetwork."
  value       = google_compute_subnetwork.db_subnet.name
}

# --- GKE Cluster Outputs ---
output "gke_cluster_name_main_output" { # Renamed to avoid conflict if var.gke_cluster_name exists
  description = "The name of the GKE cluster."
  value       = google_container_cluster.primary.name
}

output "gke_cluster_endpoint_output" {
  description = "The IP address of the GKE cluster master endpoint."
  value       = google_container_cluster.primary.endpoint
  sensitive   = true
}

output "gke_cluster_ca_certificate_output" {
  description = "The GKE cluster master CA certificate (base64 encoded)."
  value       = google_container_cluster.primary.master_auth[0].cluster_ca_certificate
  sensitive   = true
}

output "gke_workload_identity_pool_output" {
  description = "The Workload Identity Pool for the GKE cluster."
  value       = "${var.gcp_project_id}.svc.id.goog" # Standard format based on project ID
}

output "gke_primary_node_pool_name_main_output" { # Renamed
  description = "The name of the primary GKE node pool."
  value       = google_container_node_pool.primary_nodes.name
}

output "gke_location_output" {
  description = "The location (region or zone) of the GKE cluster."
  value       = google_container_cluster.primary.location
}

# --- Artifact Registry Outputs ---
output "artifact_registry_repository_id_output" {
  description = "The full ID of the Artifact Registry repository."
  value       = google_artifact_registry_repository.main.id
}

output "artifact_registry_repository_name_main_output" { # Renamed
  description = "The name (repository_id) of the Artifact Registry repository."
  value       = google_artifact_registry_repository.main.repository_id
}

output "artifact_registry_repository_url_output" {
  description = "The URL of the Artifact Registry repository for Docker (e.g., <location>-docker.pkg.dev/<project>/<repository>)."
  value       = "${google_artifact_registry_repository.main.location}-docker.pkg.dev/${var.gcp_project_id}/${google_artifact_registry_repository.main.repository_id}"
}

# --- Secret Manager Outputs ---
output "secret_manager_secret_ids_map_output" {
  description = "A map of the logical secret name (map key from var.secrets_to_create_in_gcp_sm) to its full Google Secret Manager ID."
  value       = { for k, v in google_secret_manager_secret.app_secrets : k => v.id }
}

output "secret_manager_secret_names_map_output" {
  description = "A map of the logical secret name (map key) to its short Secret ID (name) in Secret Manager."
  value       = { for k, v in google_secret_manager_secret.app_secrets : k => v.secret_id }
}

output "cloudsql_password_secret_id_output" {
  description = "The full ID of the Google Secret Manager secret specifically for the Cloud SQL main user password."
  value       = google_secret_manager_secret.app_secrets[var.cloudsql_main_user_password_secret_id].id
  # Assumes var.cloudsql_main_user_password_secret_id matches a key in var.secrets_to_create_in_gcp_sm
}

# --- Cloud SQL for PostgreSQL Outputs ---
output "cloudsql_instance_name_main_output" { # Renamed
  description = "The name of the Cloud SQL PostgreSQL instance."
  value       = google_sql_database_instance.main.name
}

output "cloudsql_instance_connection_name_output" {
  description = "The connection name of the Cloud SQL instance (for Cloud SQL Proxy, etc.)."
  value       = google_sql_database_instance.main.connection_name
}

output "cloudsql_instance_private_ip_address_output" {
  description = "The private IP address of the Cloud SQL instance."
  value       = google_sql_database_instance.main.private_ip_address
}

output "cloudsql_main_db_name_main_output" { # Renamed
  description = "The name of the main logical database created in Cloud SQL."
  value       = google_sql_database.main_db.name
}

output "cloudsql_main_user_name_main_output" { # Renamed
  description = "The name of the main user for the Cloud SQL instance."
  value       = google_sql_user.main_user.name
}

output "cloudsql_service_networking_connection_name_output" {
  description = "The name of the service networking connection for VPC peering (servicenetworking.googleapis.com)."
  value       = google_service_networking_connection.private_vpc_connection.peering
}

# --- IAM Service Account Outputs ---
output "gke_node_service_account_email_output" {
  description = "Email of the GKE Node Service Account."
  value       = google_service_account.gke_node_sa.email
}

output "gke_workload_identity_service_account_email_output" {
  description = "Email of the GSA used for GKE Workload Identity (to be impersonated by KSAs)."
  value       = google_service_account.gke_workload_identity_sa.email
}

output "cicd_service_account_email_gcp_output" {
  description = "Email of the CI/CD Service Account (if created)."
  value       = var.enable_cicd_sa_gcp ? google_service_account.cicd_sa[0].email : "CI/CD SA not created"
}

# --- Random Suffix Output (if used for naming) ---
output "gcp_resource_suffix_hex_output" {
  description = "Hex string from random_id used for suffixing globally unique resources."
  value       = resource.random_id.gcp_resource_suffix.hex
}
