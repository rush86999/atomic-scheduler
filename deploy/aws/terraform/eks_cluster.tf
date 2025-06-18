# --- Variables ---
variable "project_name" {
  description = "Name of the project, used for tagging and naming resources."
  type        = string
}

variable "aws_region" {
  description = "AWS region for the EKS cluster."
  type        = string
}

variable "eks_cluster_name" {
  description = "Name for the EKS cluster."
  type        = string
}

variable "eks_cluster_version" {
  description = "Desired Kubernetes version for the EKS cluster."
  type        = string
  default     = "1.28" # Specify a recent, supported version
}

variable "vpc_id" {
  description = "The ID of the VPC where the EKS cluster will be deployed."
  type        = string
}

variable "private_subnet_ids" {
  description = "List of private subnet IDs for the EKS control plane ENIs and worker nodes."
  type        = list(string)
}

# --- IAM Role for EKS Cluster ---
resource "aws_iam_role" "eks_cluster_role" {
  name = "${var.project_name}-eks-cluster-role"

  assume_role_policy = jsonencode({
    Version   = "2012-10-17",
    Statement = [
      {
        Action    = "sts:AssumeRole",
        Effect    = "Allow",
        Principal = {
          Service = "eks.amazonaws.com"
        }
      }
    ]
  })

  tags = {
    Name      = "${var.project_name}-eks-cluster-role"
    Project   = var.project_name
    Terraform = "true"
  }
}

resource "aws_iam_role_policy_attachment" "eks_cluster_policy_attachment" {
  policy_arn = "arn:aws:iam::aws:policy/AmazonEKSClusterPolicy"
  role       = aws_iam_role.eks_cluster_role.name
}

# AmazonEKSVPCResourceController is needed if using security groups for pods or certain CNI features.
# AmazonEKSServicePolicy is an older policy, AmazonEKSClusterPolicy is generally sufficient for the cluster role itself.
# However, some resources like Fargate profiles or VPC CNI might need additional permissions often covered by VPCResourceController.
resource "aws_iam_role_policy_attachment" "eks_vpc_resource_controller_attachment" {
  policy_arn = "arn:aws:iam::aws:policy/AmazonEKSVPCResourceController" # Recommended for VPC CNI & SG for pods
  role       = aws_iam_role.eks_cluster_role.name
}

# --- EKS Cluster ---
resource "aws_eks_cluster" "main" {
  name     = var.eks_cluster_name
  role_arn = aws_iam_role.eks_cluster_role.arn
  version  = var.eks_cluster_version

  vpc_config {
    subnet_ids              = var.private_subnet_ids # Control plane ENIs will be placed in these subnets
    # endpoint_private_access = true  # Set to true for private-only API endpoint
    # endpoint_public_access  = false # Set to false for private-only API endpoint
    # public_access_cidrs     = ["0.0.0.0/0"] # Restrict public access to specific IPs if endpoint_public_access is true.
                                            # For production, this should NOT be 0.0.0.0/0.
                                            # Example: ["YOUR_OFFICE_IP/32", "YOUR_VPN_IP/32"]
    # The cluster_security_group_id is automatically created by EKS and can be referenced.
    # security_group_ids = [] # Optional: Additional security groups for control plane ENIs. EKS creates one by default.
  }

  # EKS automatically creates an OIDC provider for IAM Roles for Service Accounts (IRSA / Workload Identity).
  # We can retrieve its URL using `aws_eks_cluster.main.identity[0].oidc[0].issuer`.

  tags = {
    Name                                      = var.eks_cluster_name
    "kubernetes.io/cluster/${var.eks_cluster_name}" = "owned" # Standard EKS tag
    Project                                   = var.project_name
    Terraform                                 = "true"
  }

  depends_on = [
    aws_iam_role_policy_attachment.eks_cluster_policy_attachment,
    aws_iam_role_policy_attachment.eks_vpc_resource_controller_attachment,
  ]
}

# --- IAM OIDC Provider Data Source (to get ARN) ---
# EKS creates the OIDC provider. We use a data source to get its ARN for other IAM configurations if needed (e.g., for IRSA trust policies).
data "aws_iam_openid_connect_provider" "eks_oidc_provider" {
  # The URL of the OIDC provider is derived from the EKS cluster's OIDC issuer URL.
  # Ensure the cluster is created before this data source is queried.
  # The dependency on aws_eks_cluster.main should handle this.
  url = aws_eks_cluster.main.identity[0].oidc[0].issuer
}


# --- Outputs (typically in outputs.tf, but defined here for clarity of what this module provides) ---
# Moved to outputs.tf as per best practice.
# output "eks_cluster_name_output" {
#   description = "The name of the EKS cluster."
#   value       = aws_eks_cluster.main.name
# }
#
# output "eks_cluster_endpoint" {
#   description = "The endpoint for the EKS cluster's Kubernetes API server."
#   value       = aws_eks_cluster.main.endpoint
# }
#
# output "eks_cluster_certificate_authority_data" {
#   description = "The base64 encoded certificate data required to communicate with the EKS cluster."
#   value       = aws_eks_cluster.main.certificate_authority[0].data
# }
#
# output "eks_cluster_security_group_id" {
#   description = "The ID of the security group created by EKS for the cluster control plane. This is crucial for worker node SGs."
#   value       = aws_eks_cluster.main.vpc_config[0].cluster_security_group_id
# }
#
# output "eks_oidc_provider_url" {
#   description = "The URL of the EKS OIDC Identity Provider."
#   value       = aws_eks_cluster.main.identity[0].oidc[0].issuer
# }
#
# output "eks_oidc_provider_arn" {
#   description = "The ARN of the EKS OIDC Identity Provider."
#   value       = data.aws_iam_openid_connect_provider.eks_oidc_provider.arn
# }
