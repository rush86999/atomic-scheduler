# --- Global Configuration Variables ---
variable "aws_region" {
  description = "AWS region for all resources."
  type        = string
  default     = "us-east-1" # Example default, adjust as needed
}

variable "aws_profile" {
  description = "AWS CLI named profile to use for authentication. Optional."
  type        = string
  default     = null # If null, Terraform will use default credentials chain (env vars, instance profile, etc.)
}

variable "project_name" {
  description = "Name of the project, used for tagging and naming resources consistently."
  type        = string
  default     = "atomic" # Example project name
}

variable "environment_name" {
  description = "The name of the environment (e.g., dev, staging, prod), used for consistent tagging."
  type        = string
  default     = "dev" # Example environment
}

variable "aws_account_id" {
  description = "AWS Account ID where the resources are deployed. If empty, it will be fetched automatically."
  type        = string
  default     = ""
}

# --- VPC Configuration Variables ---
variable "vpc_cidr" {
  description = "CIDR block for the VPC."
  type        = string
  default     = "10.0.0.0/16"
}

variable "public_subnet_cidrs" {
  description = "List of CIDR blocks for public subnets. One per AZ typically."
  type        = list(string)
  # default = ["10.0.1.0/24", "10.0.2.0/24", "10.0.3.0/24"] # Provide defaults based on expected AZ count or leave empty for explicit input
}

variable "private_subnet_cidrs" {
  description = "List of CIDR blocks for private subnets (for EKS workers, applications). One per AZ typically."
  type        = list(string)
  # default = ["10.0.101.0/24", "10.0.102.0/24", "10.0.103.0/24"]
}

variable "database_subnet_cidrs" {
  description = "List of CIDR blocks for database subnets. One per AZ typically."
  type        = list(string)
  # default = ["10.0.201.0/24", "10.0.202.0/24", "10.0.203.0/24"]
}

variable "enable_nat_gateway" {
  description = "Set to true to create NAT Gateways for private subnets."
  type        = bool
  default     = true
}

variable "single_nat_gateway" {
  description = "Set to true to create a single NAT Gateway. If false, one NAT Gateway per AZ is created."
  type        = bool
  default     = false # Default to HA setup
}

# --- Security Group Configuration Variables ---
# vpc_id is derived from aws_vpc.main.id, so not needed as an input variable here if all in one module.
# If these were separate modules, vpc_id would be an input.
# For a flat structure, it's an internal reference.
# However, security_groups.tf defines it, implying it might be used before vpc is fully known if files are processed non-dependently by a user.
# Let's assume for now this flat structure implies resources are created in order or TF handles dependency.
# If SG module was truly separate, it would need vpc_id.

variable "eks_worker_sg_name" {
  description = "The name for the EKS worker nodes security group."
  type        = string
  default     = "eks-worker-nodes-sg" # Default from security_groups.tf
}

variable "rds_sg_name" {
  description = "The name for the RDS security group."
  type        = string
  default     = "rds-postgresql-sg" # Default from security_groups.tf
}

# --- EKS Cluster Configuration Variables ---
variable "eks_cluster_name" {
  description = "Name for the EKS cluster."
  type        = string
  default     = "atomic-eks-cluster" # Example, can be derived from project_name
}

variable "eks_cluster_version" {
  description = "Desired Kubernetes version for the EKS cluster."
  type        = string
  default     = "1.28"
}
# private_subnet_ids for EKS cluster vpc_config will be taken from the created private subnets.

# --- EKS Node Group Configuration Variables ---
variable "eks_node_group_name" {
  description = "Name for the EKS managed node group."
  type        = string
  default     = "general-workers-ng" # Default from eks_nodegroups.tf
}

variable "eks_node_group_instance_types" {
  description = "List of instance types for the EKS node group."
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
# eks_cluster_security_group_id and eks_worker_security_group_id for node group's launch template
# will be taken from created resources.

# --- ECR Configuration Variables ---
variable "custom_image_names" {
  description = "A list of custom Docker image names for ECR repositories."
  type        = list(string)
  default = [ # Default from ecr.tf
    "atomic-scheduler",
    "atomic-functions",
    "atomic-handshake",
    "atomic-oauth",
    "atomic-app"
  ]
}

variable "ecr_image_tag_mutability" {
  description = "The image tag mutability setting for ECR (MUTABLE or IMMUTABLE)."
  type        = string
  default     = "MUTABLE"
  validation {
    condition     = contains(["MUTABLE", "IMMUTABLE"], var.ecr_image_tag_mutability)
    error_message = "Image tag mutability must be either MUTABLE or IMMUTABLE."
  }
}

variable "ecr_scan_on_push" {
  description = "Whether to enable image scanning on push for ECR repositories."
  type        = bool
  default     = true
}

# --- Secrets Manager Configuration Variables ---
variable "secrets_to_create" {
  description = "A map of secrets to create in AWS Secrets Manager. Key is suffix, value is description."
  type        = map(string)
  default = { # Default from secrets_manager.tf
    "POSTGRES_USER"                = "Username for the RDS PostgreSQL database."
    "POSTGRES_PASSWORD"            = "Password for the RDS PostgreSQL database user."
    "HASURA_GRAPHQL_ADMIN_SECRET"  = "Admin secret for Hasura GraphQL engine."
    "HASURA_GRAPHQL_JWT_SECRET"    = "JWT secret for Hasura GraphQL engine (JSON format)."
    "TRAEFIK_USER"                 = "Username for Traefik dashboard basic authentication."
    "TRAEFIK_PASSWORD"             = "Password for Traefik dashboard basic authentication."
    "BASIC_AUTH_FUNCTIONS_ADMIN"   = "Basic authentication credentials for -admin endpoints in functions service."
    "OPENAI_API_KEY"               = "API key for OpenAI services."
    "API_TOKEN"                    = "General API Token for custom services (e.g., Optaplanner)."
    "GOOGLE_CLIENT_ID_ANDROID"         = "Google Client ID for Android application."
    "GOOGLE_CLIENT_ID_IOS"             = "Google Client ID for iOS application."
    "GOOGLE_CLIENT_ID_WEB"             = "Google Client ID for Web application."
    "GOOGLE_CLIENT_ID_ATOMIC_WEB"      = "Google Client ID for Atomic Web application."
    "GOOGLE_CLIENT_SECRET_ATOMIC_WEB"  = "Google Client Secret for Atomic Web application."
    "GOOGLE_CLIENT_SECRET_WEB"         = "Google Client Secret for Web application."
    "GOOGLE_CALENDAR_ID"               = "Google Calendar ID for integration."
    "GOOGLE_CALENDAR_CREDENTIALS"      = "Google Calendar service account credentials JSON string."
    "GOOGLE_MAP_KEY"                   = "Google Maps API Key."
    "GOOGLE_PLACE_API_KEY"             = "Google Places API Key."
    "STORAGE_ACCESS_KEY"           = "Access key for Minio or S3 compatible storage."
    "STORAGE_SECRET_KEY"           = "Secret key for Minio or S3 compatible storage."
    "STORAGE_REGION"               = "Default region for S3 compatible storage."
    "KAFKA_USERNAME"               = "Username for Kafka SASL authentication."
    "KAFKA_PASSWORD"               = "Password for Kafka SASL authentication."
    "OPENSEARCH_USERNAME"          = "Username for OpenSearch security."
    "OPENSEARCH_PASSWORD"          = "Password for OpenSearch security."
    "ZOOM_CLIENT_ID"               = "Zoom Client ID."
    "ZOOM_CLIENT_SECRET"           = "Zoom Client Secret."
    "ZOOM_PASS_KEY"                = "Zoom Pass Key for encryption."
    "ZOOM_SALT_FOR_PASS"           = "Zoom Salt for Pass Key."
    "ZOOM_IV_FOR_PASS"             = "Zoom IV for Pass Key."
    "ZOOM_WEBHOOK_SECRET_TOKEN"    = "Zoom Webhook Secret Token."
    "OPTAPLANNER_USERNAME"         = "Username for Optaplanner service."
    "OPTAPLANNER_PASSWORD"         = "Password for Optaplanner service."
    "SMTP_HOST"                    = "SMTP server host."
    "SMTP_PORT"                    = "SMTP server port."
    "SMTP_USER"                    = "SMTP username."
    "SMTP_PASS"                    = "SMTP password."
    "SMTP_FROM_EMAIL"              = "Default FROM email address for SMTP."
    "TWILIO_ACCOUNT_SID"           = "Twilio Account SID."
    "TWILIO_AUTH_TOKEN"            = "Twilio Auth Token."
    "TWILIO_PHONE_NO"              = "Twilio phone number."
    "STRIPE_API_KEY"               = "Stripe Secret Key."
    "STRIPE_WEBHOOK_SECRET"        = "Stripe Webhook Signing Secret."
    "ONESIGNAL_APP_ID"             = "OneSignal App ID."
    "ONESIGNAL_REST_API_KEY"       = "OneSignal REST API Key."
    "SLACK_BOT_TOKEN"              = "Slack Bot Token."
    "SLACK_SIGNING_SECRET"         = "Slack Signing Secret."
    "SLACK_CHANNEL_ID"             = "Default Slack Channel ID for notifications."
    "JWT_SECRET"                   = "Default JWT secret key for application-level tokens."
    "ENCRYPTION_KEY"               = "Default encryption key for application data."
    "SESSION_SECRET_KEY"           = "Session secret key for web applications."
  }
}

# --- RDS Configuration Variables ---
variable "rds_instance_class" {
  description = "The instance class for the RDS instance."
  type        = string
  default     = "db.t3.micro"
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
  description = "The version of the database engine."
  type        = string
  default     = "14.5"
}

variable "rds_db_name" {
  description = "The name of the initial database to be created in the RDS instance."
  type        = string
  default     = "atomicdb"
}

variable "rds_username" {
  description = "The master username for the RDS instance."
  type        = string
  default     = "postgresadmin"
}

variable "rds_password_secret_arn_input" { # Renamed to avoid conflict with potential output if flat
  description = "The ARN of the AWS Secrets Manager secret holding the master password for RDS. To be constructed like 'arn:aws:secretsmanager:REGION:ACCOUNT_ID:secret:PROJECT_NAME/POSTGRES_PASSWORD-??????' or reference aws_secretsmanager_secret.main[\"POSTGRES_PASSWORD\"].arn"
  type        = string
  # This will typically be dynamically constructed or passed in if secrets are managed separately.
  # For this flat structure, it will reference the created secret.
  # No default, should be derived from the secrets_manager.tf output.
}

# rds_db_subnet_group_name and rds_vpc_security_group_ids will be taken from created resources.

variable "rds_multi_az" {
  description = "Specifies if the RDS instance is multi-AZ."
  type        = bool
  default     = false
}

variable "rds_backup_retention_period" {
  description = "The days to retain backups for."
  type        = number
  default     = 7
}

variable "rds_skip_final_snapshot" {
  description = "Determines whether a final DB snapshot is created before deletion."
  type        = bool
  default     = true # Set to false for production
}

variable "rds_storage_encrypted" {
  description = "Specifies whether the DB instance is encrypted."
  type        = bool
  default     = true
}

# --- IAM Configuration Variables ---
# eks_oidc_provider_arn and eks_oidc_provider_url_no_prefix will be taken from EKS cluster outputs.
variable "codebuild_artifacts_s3_bucket_name" {
  description = "Name of the S3 bucket used for CodeBuild artifacts."
  type        = string
  # default = "myproject-codebuild-artifacts-bucket" # Must be globally unique and created separately.
}

# --- Data Sources to provide default values if not set ---
# These are useful if the module needs to know the current account ID or region
# and they are not explicitly passed as variables.
data "aws_caller_identity" "current" {}
data "aws_region" "current_region" {} # Renamed to avoid conflict with var.aws_region

# Note: If var.aws_account_id is an empty string (its default),
# then coalesce(var.aws_account_id, data.aws_caller_identity.current.account_id)
# can be used in resource definitions where account ID is needed.
# Similarly for region if var.aws_region might not be set (though it has a default here).
