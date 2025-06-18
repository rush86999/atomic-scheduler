# --- Variables ---
variable "project_name" {
  description = "Name of the project, used for tagging and naming resources."
  type        = string
}

variable "eks_cluster_name" {
  description = "The name of the EKS cluster to which this node group will be attached."
  type        = string
}

variable "eks_cluster_security_group_id" {
  description = "The security group ID of the EKS cluster control plane. Required for the launch template."
  type        = string
}

variable "eks_node_group_name" {
  description = "Name for the EKS managed node group."
  type        = string
  default     = "general-workers-ng"
}

variable "eks_node_group_instance_types" {
  description = "List of instance types for the EKS node group (e.g., [\"t3.medium\"])."
  type        = list(string)
  default     = ["t3.medium"]
}

variable "eks_node_group_desired_size" {
  description = "Desired number of worker nodes in the node group."
  type        = number
  default     = 2
}

variable "eks_node_group_min_size" {
  description = "Minimum number of worker nodes in the node group."
  type        = number
  default     = 1
}

variable "eks_node_group_max_size" {
  description = "Maximum number of worker nodes in the node group."
  type        = number
  default     = 3
}

variable "private_subnet_ids" {
  description = "List of private subnet IDs where worker nodes will be deployed."
  type        = list(string)
}

variable "eks_worker_security_group_id" {
  description = "The ID of the custom EKS worker security group (eks_worker_sg)."
  type        = string
}

# --- IAM Role for EKS Worker Nodes ---
resource "aws_iam_role" "eks_node_group_role" {
  name = "${var.project_name}-${var.eks_node_group_name}-role"

  assume_role_policy = jsonencode({
    Version   = "2012-10-17",
    Statement = [
      {
        Action    = "sts:AssumeRole",
        Effect    = "Allow",
        Principal = {
          Service = "ec2.amazonaws.com"
        }
      }
    ]
  })

  tags = {
    Name      = "${var.project_name}-${var.eks_node_group_name}-role"
    Project   = var.project_name
    Terraform = "true"
  }
}

resource "aws_iam_role_policy_attachment" "eks_worker_node_policy" {
  policy_arn = "arn:aws:iam::aws:policy/AmazonEKSWorkerNodePolicy"
  role       = aws_iam_role.eks_node_group_role.name
}

resource "aws_iam_role_policy_attachment" "eks_cni_policy" {
  policy_arn = "arn:aws:iam::aws:policy/AmazonEKS_CNI_Policy" # Required for VPC CNI
  role       = aws_iam_role.eks_node_group_role.name
}

resource "aws_iam_role_policy_attachment" "ecr_read_only_policy" {
  policy_arn = "arn:aws:iam::aws:policy/AmazonEC2ContainerRegistryReadOnly" # To pull images from ECR
  role       = aws_iam_role.eks_node_group_role.name
}

# --- Launch Template for EKS Worker Nodes ---
# This launch template is used to customize the worker nodes, specifically to attach
# both the cluster security group and our custom worker security group.
resource "aws_launch_template" "eks_nodes_lt" {
  name_prefix = "${var.eks_cluster_name}-${var.eks_node_group_name}-lt-"
  description = "Launch template for EKS node group: ${var.eks_node_group_name}"

  # Do not specify image_id to use the EKS Optimized AMI by default for Managed Node Groups.
  # Do not specify instance_type here; it will be specified in the aws_eks_node_group resource.

  network_interfaces {
    associate_public_ip_address = false # Nodes are in private subnets
    # Attach both the EKS cluster's primary security group and our custom worker security group.
    # The cluster security group allows communication with the control plane.
    # The worker security group allows application-specific traffic and RDS access.
    security_groups = [
      var.eks_cluster_security_group_id, # EKS Control Plane Security Group
      var.eks_worker_security_group_id   # Custom Worker Security Group (eks_worker_sg)
    ]
    # delete_on_termination = true # Default is true for ENIs created by LT
  }

  # User data can be supplied for custom bootstrap actions if needed, but often not required for basic managed node groups.
  # user_data = base64encode(<<-EOF
  # #!/bin/bash
  # # Add custom bootstrap commands here if necessary
  # EOF
  # )

  tag_specifications {
    resource_type = "instance"
    tags = {
      Name    = "${var.project_name}-${var.eks_node_group_name}-worker"
      Project = var.project_name
    }
  }

  tags = {
    Name      = "${var.project_name}-${var.eks_node_group_name}-lt"
    Project   = var.project_name
    Terraform = "true"
  }
}

# --- EKS Managed Node Group ---
resource "aws_eks_node_group" "main" {
  cluster_name    = var.eks_cluster_name
  node_group_name = var.eks_node_group_name
  node_role_arn   = aws_iam_role.eks_node_group_role.arn
  subnet_ids      = var.private_subnet_ids

  instance_types = var.eks_node_group_instance_types
  scaling_config {
    desired_size = var.eks_node_group_desired_size
    min_size     = var.eks_node_group_min_size
    max_size     = var.eks_node_group_max_size
  }

  # Associate the launch template
  launch_template {
    id      = aws_launch_template.eks_nodes_lt.id
    version = aws_launch_template.eks_nodes_lt.latest_version # Always use the latest version of the LT
  }

  # Ensure nodes are updated when scaling properties or launch template changes.
  # force_update_version = true # Set to true to force node replacement on version changes or certain config updates. Useful if LT changes often.
  # update_config {
  #   max_unavailable_percentage = 33 # Or max_unavailable for fixed number
  # }

  tags = {
    Name                                              = "${var.project_name}-${var.eks_node_group_name}"
    "k8s.io/cluster-autoscaler/${var.eks_cluster_name}" = "owned" # For cluster autoscaler discovery
    "k8s.io/cluster-autoscaler/enabled"               = "true"  # For cluster autoscaler discovery
    Project                                           = var.project_name
    Terraform                                         = "true"
  }

  depends_on = [
    aws_iam_role_policy_attachment.eks_worker_node_policy,
    aws_iam_role_policy_attachment.eks_cni_policy,
    aws_iam_role_policy_attachment.ecr_read_only_policy,
    aws_launch_template.eks_nodes_lt,
    # Add dependency on the EKS cluster resource if it's defined in a different module/file.
    # This is implicit if var.eks_cluster_name and var.eks_cluster_security_group_id are from the cluster module.
  ]
}

# --- Outputs (Optional here, usually in outputs.tf) ---
# output "eks_node_group_role_arn" {
#   description = "ARN of the IAM role for the EKS node group."
#   value       = aws_iam_role.eks_node_group_role.arn
# }
#
# output "eks_node_group_name_output" {
#   description = "Name of the EKS node group created."
#   value       = aws_eks_node_group.main.node_group_name
# }
#
# output "launch_template_id" {
#  description = "ID of the launch template created for the EKS node group."
#  value       = aws_launch_template.eks_nodes_lt.id
# }
