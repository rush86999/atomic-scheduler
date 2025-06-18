# --- Variables ---
variable "vpc_id" {
  description = "The ID of the VPC where the security groups will be created."
  type        = string
}

variable "project_name" {
  description = "Name of the project, used for tagging and naming resources."
  type        = string
}

variable "eks_worker_sg_name" {
  description = "The name for the EKS worker nodes security group."
  type        = string
  default     = "eks-worker-nodes-sg"
}

variable "rds_sg_name" {
  description = "The name for the RDS security group."
  type        = string
  default     = "rds-postgresql-sg"
}

# --- EKS Worker Nodes Security Group ---
resource "aws_security_group" "eks_worker_sg" {
  name        = var.eks_worker_sg_name
  description = "Security group for EKS worker nodes"
  vpc_id      = var.vpc_id

  # Ingress rules for EKS worker nodes
  ingress {
    description = "HTTPS from EKS Control Plane to Kubelet"
    from_port   = 443
    to_port     = 443
    protocol    = "tcp"
    # !! IMPORTANT !!
    # This CIDR block is a placeholder and MUST BE RESTRICTED.
    # Replace this with the EKS Control Plane Security Group ID once the EKS cluster is created.
    # Example: security_groups = [aws_eks_cluster.main.vpc_config[0].cluster_security_group_id]
    # Using 0.0.0.0/0 here for placeholder purposes only during initial SG creation.
    cidr_blocks = ["0.0.0.0/0"]
    # TODO: Replace above cidr_blocks with security_groups = [var.eks_control_plane_sg_id]
    # where var.eks_control_plane_sg_id is an input variable populated from the EKS cluster output.
  }

  ingress {
    description = "Kubelet API from EKS Control Plane"
    from_port   = 10250
    to_port     = 10250
    protocol    = "tcp"
    # !! IMPORTANT !!
    # This CIDR block is a placeholder and MUST BE RESTRICTED.
    # Replace this with the EKS Control Plane Security Group ID.
    cidr_blocks = ["0.0.0.0/0"]
    # TODO: Replace above cidr_blocks with security_groups = [var.eks_control_plane_sg_id]
  }

  ingress {
    description     = "Node-to-node communication within the EKS cluster"
    from_port       = 0 # All ports
    to_port         = 0 # All ports
    protocol        = "-1" # All protocols
    self            = true # Allows traffic from other instances in the same security group
  }

  # Egress rules for EKS worker nodes
  egress {
    description = "Allow all outbound traffic"
    from_port   = 0
    to_port     = 0
    protocol    = "-1" # All protocols
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = {
    Name                                      = var.eks_worker_sg_name
    "kubernetes.io/cluster/${var.project_name}" = "owned" # Or "shared" if used by multiple clusters
    Project                                   = var.project_name
    Terraform                                 = "true"
  }
}

# --- RDS PostgreSQL Security Group ---
resource "aws_security_group" "rds_sg" {
  name        = var.rds_sg_name
  description = "Security group for RDS PostgreSQL instance"
  vpc_id      = var.vpc_id

  # Ingress rule for RDS PostgreSQL
  ingress {
    description     = "Allow PostgreSQL access from EKS worker nodes"
    from_port       = 5432 # PostgreSQL default port
    to_port         = 5432
    protocol        = "tcp"
    security_groups = [aws_security_group.eks_worker_sg.id] # Source is the EKS worker SG
  }

  # Egress rules for RDS PostgreSQL
  # Typically, RDS might not need broad outbound access, but some features might require it (e.g., to S3 for backups if not using AWS Backup, custom metrics).
  # For a baseline, allowing all outbound. Restrict as needed.
  egress {
    description = "Allow all outbound traffic from RDS (restrict if possible)"
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = {
    Name      = var.rds_sg_name
    Project   = var.project_name
    Terraform = "true"
  }
}

# --- Outputs (Optional here, usually in outputs.tf but can be useful for immediate reference) ---
# output "eks_worker_sg_id" {
#   description = "The ID of the EKS worker nodes security group."
#   value       = aws_security_group.eks_worker_sg.id
# }

# output "rds_sg_id" {
#   description = "The ID of the RDS security group."
#   value       = aws_security_group.rds_sg.id
# }
