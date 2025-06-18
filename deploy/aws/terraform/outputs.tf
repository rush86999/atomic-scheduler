# --- VPC Outputs ---
output "vpc_id_output" {
  description = "The ID of the created VPC."
  value       = aws_vpc.main.id
}

output "public_subnet_ids_output" {
  description = "List of IDs of the public subnets."
  value       = aws_subnet.public[*].id
}

output "private_subnet_ids_output" {
  description = "List of IDs of the private subnets."
  value       = aws_subnet.private[*].id
}

output "database_subnet_ids_output" {
  description = "List of IDs of the database subnets."
  value       = aws_subnet.database[*].id
}

output "db_subnet_group_name_output" {
  description = "The name of the RDS DB Subnet Group."
  value       = length(aws_db_subnet_group.main) > 0 ? aws_db_subnet_group.main[0].name : null
  # Using length check as aws_db_subnet_group has count = length(var.database_subnet_cidrs) > 0 ? 1 : 0
}

# --- Security Group Outputs ---
output "eks_worker_sg_id_output" {
  description = "The ID of the EKS worker nodes security group."
  value       = aws_security_group.eks_worker_sg.id
}

output "rds_sg_id_output" {
  description = "The ID of the RDS security group."
  value       = aws_security_group.rds_sg.id
}

# --- EKS Cluster Outputs ---
output "eks_cluster_name_output" {
  description = "The name of the EKS cluster."
  value       = aws_eks_cluster.main.name
}

output "eks_cluster_endpoint_output" {
  description = "The endpoint for the EKS cluster's Kubernetes API server."
  value       = aws_eks_cluster.main.endpoint
}

output "eks_cluster_certificate_authority_data_output" {
  description = "The base64 encoded certificate data required to communicate with the EKS cluster."
  value       = aws_eks_cluster.main.certificate_authority[0].data
}

output "eks_cluster_security_group_id_output" {
  description = "The ID of the security group created by EKS for the cluster control plane."
  value       = aws_eks_cluster.main.vpc_config[0].cluster_security_group_id
}

output "eks_oidc_provider_url_output" {
  description = "The URL of the EKS OIDC Identity Provider."
  value       = aws_eks_cluster.main.identity[0].oidc[0].issuer
}

output "eks_oidc_provider_url_no_prefix_output" {
  description = "The URL of the EKS OIDC Identity Provider without the 'https://' prefix."
  # Example: oidc.eks.us-east-1.amazonaws.com/id/EXAMPLED539D4633E53BF441C14A35A56B5
  value       = replace(aws_eks_cluster.main.identity[0].oidc[0].issuer, "https://", "")
}

output "eks_oidc_provider_arn_output" {
  description = "The ARN of the EKS OIDC Identity Provider."
  value       = data.aws_iam_openid_connect_provider.eks_oidc_provider.arn
}

# --- EKS Node Group Outputs ---
output "eks_node_group_role_arn_output" {
  description = "ARN of the IAM role for the EKS node group."
  value       = aws_iam_role.eks_node_group_role.arn
}

output "eks_node_group_name_main_output" { # Added main to distinguish if multiple node groups exist
  description = "Name of the EKS managed node group created."
  value       = aws_eks_node_group.main.node_group_name
}

output "launch_template_id_output" {
 description = "ID of the launch template created for the EKS node group."
 value       = aws_launch_template.eks_nodes_lt.id
}

# --- ECR Outputs ---
output "ecr_repository_urls_output" {
  description = "Map of ECR repository image names to their URLs."
  value = {
    for name, repo in aws_ecr_repository.main :
    name => repo.repository_url
  }
}

output "ecr_repository_arns_output" {
  description = "Map of ECR repository image names to their ARNs."
  value = {
    for name, repo in aws_ecr_repository.main :
    name => repo.arn
  }
}

# --- Secrets Manager Outputs ---
output "secret_manager_arns_output" {
  description = "Map of secret name suffixes to their full ARNs in AWS Secrets Manager."
  value = {
    for k, v in aws_secretsmanager_secret.main :
    k => v.arn
  }
}

output "secret_manager_names_output" {
  description = "Map of secret name suffixes to their full names in AWS Secrets Manager."
  value = {
    for k, v in aws_secretsmanager_secret.main :
    k => v.name
  }
}

output "rds_password_secret_arn_output" {
  description = "ARN of the AWS Secrets Manager secret specifically for the RDS password."
  value       = aws_secretsmanager_secret.main["POSTGRES_PASSWORD"].arn
  # Assuming "POSTGRES_PASSWORD" is the key used in the var.secrets_to_create map for the RDS password.
}


# --- RDS Outputs ---
output "rds_instance_endpoint_output" {
  description = "The connection endpoint for the RDS instance."
  value       = aws_db_instance.main.endpoint
}

output "rds_instance_address_output" {
  description = "The DNS address for the RDS instance."
  value       = aws_db_instance.main.address
}

output "rds_instance_port_output" {
  description = "The port for the RDS instance."
  value       = aws_db_instance.main.port
}

output "rds_instance_db_name_output" {
  description = "The initial database name of the RDS instance."
  value       = aws_db_instance.main.db_name
}

output "rds_instance_username_output" {
  description = "The master username for the RDS instance."
  value       = aws_db_instance.main.username
}

output "rds_instance_id_output" {
  description = "The ID of the RDS instance."
  value       = aws_db_instance.main.id
}

# --- IAM Outputs ---
output "codebuild_role_arn_output" {
  description = "ARN of the IAM Role for AWS CodeBuild."
  value       = aws_iam_role.codebuild_role.arn
}

# Example IRSA role ARN (if uncommented in iam.tf)
# output "example_irsa_role_arn_output" {
#   description = "ARN of the example IAM Role for Service Account (IRSA)."
#   value       = aws_iam_role.example_irsa_role.arn # This will error if the resource is commented out
# }
