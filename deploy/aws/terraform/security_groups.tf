# Security Group for EKS Worker Nodes
resource "aws_security_group" "eks_worker_sg" {
  name        = var.eks_worker_sg_name
  description = "Security group for EKS worker nodes"
  vpc_id      = var.vpc_id

  ingress {
    description = "Allow inbound traffic from the EKS control plane"
    from_port   = 443
    to_port     = 443
    protocol    = "tcp"
    # This should ideally be the EKS control plane security group
    # For now, allowing from any, but should be restricted.
    cidr_blocks = ["0.0.0.0/0"]
  }

  ingress {
    description = "Allow inbound traffic from the EKS control plane"
    from_port   = 10250
    to_port     = 10250
    protocol    = "tcp"
    # This should ideally be the EKS control plane security group
    # For now, allowing from any, but should be restricted.
    cidr_blocks = ["0.0.0.0/0"]
  }

  ingress {
    description     = "Allow node-to-node communication"
    from_port       = 0
    to_port         = 0
    protocol        = "-1" # All protocols
    self            = true
  }

  # Egress rules for EKS worker nodes
  egress {
    description = "Allow all outbound traffic to the internet"
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = {
    Name        = var.eks_worker_sg_name
    Project     = var.project_name
    Terraform   = "true"
  }
}

# Security Group for RDS PostgreSQL Instance
resource "aws_security_group" "rds_sg" {
  name        = var.rds_sg_name
  description = "Security group for RDS PostgreSQL instance"
  vpc_id      = var.vpc_id

  ingress {
    description     = "Allow PostgreSQL access from EKS worker nodes"
    from_port       = 5432
    to_port         = 5432
    protocol        = "tcp"
    security_groups = [aws_security_group.eks_worker_sg.id]
  }

  # Egress rules for RDS (generally not needed for RDS to initiate connections,
  # but can be configured if specific requirements exist e.g. for read replicas or other services)
  egress {
    description = "Allow all outbound traffic (if needed)"
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"] # Restrict if possible
  }

  tags = {
    Name        = var.rds_sg_name
    Project     = var.project_name
    Terraform   = "true"
  }
}

# Variables to be defined in variables.tf or passed as input
variable "vpc_id" {
  description = "The ID of the VPC"
  type        = string
}

variable "project_name" {
  description = "The name of the project for tagging resources"
  type        = string
}

variable "eks_worker_sg_name" {
  description = "The name for the EKS worker security group"
  type        = string
  default     = "eks-worker-sg"
}

variable "rds_sg_name" {
  description = "The name for the RDS security group"
  type        = string
  default     = "rds-postgresql-sg"
}
