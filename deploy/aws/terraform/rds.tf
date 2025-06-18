# --- Variables ---
variable "project_name" {
  description = "Name of the project, used for tagging and naming resources."
  type        = string
}

variable "aws_region" {
  description = "AWS region for the RDS instance."
  type        = string
}

variable "rds_instance_class" {
  description = "The instance class for the RDS instance (e.g., db.t3.micro, db.m5.large)."
  type        = string
  default     = "db.t3.micro" # Choose a suitable default
}

variable "rds_allocated_storage" {
  description = "The allocated storage in GB for the RDS instance."
  type        = number
  default     = 20
}

variable "rds_engine" {
  description = "The database engine for the RDS instance."
  type        = string
  default     = "postgres"
}

variable "rds_engine_version" {
  description = "The version of the database engine (e.g., for PostgreSQL: 14.5)."
  type        = string
  default     = "14.5" # Specify a recent, supported version
}

variable "rds_db_name" {
  description = "The name of the initial database to be created in the RDS instance."
  type        = string
  default     = "atomicdb" # Default database name
}

variable "rds_username" {
  description = "The master username for the RDS instance."
  type        = string
  default     = "postgresadmin" # Or another preferred admin username
}

variable "rds_password_secret_arn" {
  description = "The ARN of the AWS Secrets Manager secret holding the master password for the RDS instance."
  type        = string
  # This ARN will come from the output of the secrets_manager.tf module/setup.
  # Example: "arn:aws:secretsmanager:us-east-1:123456789012:secret:myproject/POSTGRES_PASSWORD-abcdef"
}

variable "rds_db_subnet_group_name" {
  description = "The name of the DB subnet group for the RDS instance (output from vpc.tf)."
  type        = string
}

variable "rds_vpc_security_group_ids" {
  description = "List of VPC security group IDs to associate with the RDS instance (output from security_groups.tf)."
  type        = list(string) # Should be the ID of rds_sg
}

variable "rds_multi_az" {
  description = "Specifies if the RDS instance is multi-AZ."
  type        = bool
  default     = false # For dev/test; set to true for production
}

variable "rds_backup_retention_period" {
  description = "The days to retain backups for. Must be >0 to enable backups."
  type        = number
  default     = 7 # Default to 7 days
}

variable "rds_skip_final_snapshot" {
  description = "Determines whether a final DB snapshot is created before the DB instance is deleted."
  type        = bool
  default     = true # For dev/test; set to false for production to ensure data safety
}

variable "rds_storage_encrypted" {
  description = "Specifies whether the DB instance is encrypted."
  type        = bool
  default     = true
}

# --- Data Source to Fetch RDS Password from AWS Secrets Manager ---
data "aws_secretsmanager_secret_version" "rds_password" {
  secret_id = var.rds_password_secret_arn # ARN of the secret
}

# --- RDS PostgreSQL Instance ---
resource "aws_db_instance" "main" {
  identifier             = "${var.project_name}-rds-postgres" # DB instance identifier
  allocated_storage      = var.rds_allocated_storage
  engine                 = var.rds_engine
  engine_version         = var.rds_engine_version
  instance_class         = var.rds_instance_class
  db_name                = var.rds_db_name # Name of the initial database
  username               = var.rds_username
  password               = data.aws_secretsmanager_secret_version.rds_password.secret_string # Fetched from Secrets Manager
  port                   = 5432 # Default PostgreSQL port

  db_subnet_group_name   = var.rds_db_subnet_group_name
  vpc_security_group_ids = var.rds_vpc_security_group_ids

  multi_az               = var.rds_multi_az
  backup_retention_period= var.rds_backup_retention_period
  skip_final_snapshot    = var.rds_skip_final_snapshot
  storage_encrypted      = var.rds_storage_encrypted
  # kms_key_id             = "arn:aws:kms:REGION:ACCOUNT_ID:key/YOUR_KMS_KEY_ID" # Optional: if using a customer-managed KMS key

  # Deletion protection should be enabled for production instances
  # deletion_protection    = true

  # Performance Insights can be enabled for monitoring
  # performance_insights_enabled          = true
  # performance_insights_kms_key_id     = "arn:aws:kms:REGION:ACCOUNT_ID:key/YOUR_KMS_KEY_ID" # Optional
  # performance_insights_retention_period = 7 # Default is 7 days

  # Enhanced Monitoring
  # monitoring_interval    = 60 # In seconds. Default is 0 (disabled). Valid values: 0, 1, 5, 10, 15, 30, 60.
  # monitoring_role_arn    = aws_iam_role.rds_enhanced_monitoring_role.arn # If using enhanced monitoring

  # Logging - configure which logs to export to CloudWatch Logs
  # enabled_cloudwatch_logs_exports = ["postgresql", "upgrade"] # Example for PostgreSQL

  # Parameter group and option group can be specified if custom configurations are needed
  # parameter_group_name = aws_db_parameter_group.postgres.name
  # option_group_name    = aws_db_option_group.postgres.name

  publicly_accessible    = false # Keep database private

  tags = {
    Name      = "${var.project_name}-rds-postgres"
    Project   = var.project_name
    Terraform = "true"
  }

  # It's important that the secret in Secrets Manager is created and populated *before*
  # this RDS instance attempts to use it. If this RDS module also creates the secret,
  # ensure proper dependencies or that the secret is pre-populated.
  # Here, we assume rds_password_secret_arn points to an existing secret with a value.
  depends_on = [
    data.aws_secretsmanager_secret_version.rds_password
  ]
}

# --- Outputs (typically in outputs.tf) ---
# Moved to outputs.tf as per best practice.
# output "rds_instance_endpoint" {
#   description = "The connection endpoint for the RDS instance."
#   value       = aws_db_instance.main.endpoint
# }
#
# output "rds_instance_address" {
#   description = "The DNS address for the RDS instance."
#   value       = aws_db_instance.main.address
# }
#
# output "rds_instance_port" {
#   description = "The port for the RDS instance."
#   value       = aws_db_instance.main.port
# }
#
# output "rds_instance_db_name" {
#   description = "The initial database name of the RDS instance."
#   value       = aws_db_instance.main.db_name
# }
#
# output "rds_instance_username" {
#   description = "The master username for the RDS instance."
#   value       = aws_db_instance.main.username
# }
#
# output "rds_instance_id" {
#   description = "The ID of the RDS instance."
#   value       = aws_db_instance.main.id
# }
