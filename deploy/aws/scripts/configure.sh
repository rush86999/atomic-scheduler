#!/bin/bash

# Color codes
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m' # No Color

# Function to print a section header
print_header() {
    echo -e "\n${GREEN}================================================================================"
    echo -e "${GREEN}$1"
    echo -e "${GREEN}================================================================================${NC}\n"
}

# Function to check if a command exists
command_exists() {
    command -v "$1" >/dev/null 2>&1
}

# Function to print error message and exit
error_exit() {
    echo -e "${RED}ERROR: $1${NC}" >&2
    exit 1
}

# 1. Welcome Message
print_header "Welcome to the Atomic Stack AWS Deployment Configuration Script!"
echo -e "This script will guide you through the initial setup and configuration steps."
echo -e "It will not store any sensitive data but will help you prepare your environment."

# 2. Check for Prerequisites
print_header "Checking Prerequisites..."
declare -a prereqs=("aws" "terraform" "kubectl" "kustomize" "jq")
all_prereqs_met=true
for cmd in "${prereqs[@]}"; do
    if command_exists "$cmd"; then
        echo -e "${GREEN}$cmd is installed.${NC}"
    else
        echo -e "${RED}$cmd is NOT installed. Please install it before proceeding.${NC}"
        all_prereqs_met=false
    fi
done

if [ "$all_prereqs_met" = false ]; then
    error_exit "One or more prerequisite tools are missing. Please install them and re-run this script."
fi
echo -e "${GREEN}All prerequisite tools are installed.${NC}"

# 3. AWS CLI Configuration
print_header "AWS CLI Configuration"
echo -e "Please ensure your AWS CLI is configured correctly."
echo -e "You can configure it using 'aws configure' if you haven't already."
echo -e "Let's check your current AWS identity (this helps verify CLI setup):"
if aws sts get-caller-identity --query Arn --output text &>/dev/null; then
    CURRENT_IDENTITY=$(aws sts get-caller-identity --query Arn --output text)
    echo -e "${GREEN}Current AWS Identity ARN: $CURRENT_IDENTITY${NC}"
else
    echo -e "${YELLOW}Could not automatically determine current AWS identity.${NC}"
    echo -e "${YELLOW}Please ensure 'aws configure' has been run and your credentials/region are set up.${NC}"
    read -p "Press Enter to continue if you've configured AWS CLI, or Ctrl+C to exit and configure it now..."
fi

# 4. Gather Basic Configuration Information (Guidance)
print_header "Basic Configuration Settings (Guidance)"
echo -e "This deployment uses a configuration file: deploy/aws/config.sh"
echo -e "We will create a default 'config.sh' from 'config.sh.example' if it doesn't exist."
echo -e "You will need to review and edit this file with your specific settings."
echo -e "Please consider the following values:"

echo -e "\n  - ${YELLOW}AWS Region:${NC} The AWS region where resources will be deployed (e.g., us-east-1, eu-west-2)."
echo -e "    Default in example: us-east-1"
echo -e "  - ${YELLOW}AWS Profile (Optional):${NC} If you use a specific AWS CLI named profile."
echo -e "    Default in example: (commented out)"
echo -e "  - ${YELLOW}Project Name:${NC} A short name for your project (e.g., atomic, myapp). Used for resource prefixing."
echo -e "    Default in example: atomic"
echo -e "  - ${YELLOW}Environment Name:${NC} Deployment environment (e.g., dev, staging, prod)."
echo -e "    Default in example: dev"
echo -e "  - ${YELLOW}Domain Name:${NC} Your primary domain name (e.g., example.com) for application URLs."
echo -e "    Default in example: example.com"
read -p "Press Enter to acknowledge and continue..."

# 5. Terraform S3 Backend Configuration
print_header "Terraform S3 Backend Configuration"
echo -e "${YELLOW}IMPORTANT:${NC} For any serious use of Terraform (especially in teams or CI/CD), a remote backend is crucial."
echo -e "We recommend using AWS S3 with DynamoDB for state locking."
echo -e "You will need to perform these steps manually in your AWS account:"
echo -e "  1. ${YELLOW}Create an S3 bucket:${NC} This bucket will store your Terraform state file."
echo -e "     Make sure it has versioning enabled. Example AWS CLI command:"
echo -e "     ${GREEN}aws s3api create-bucket --bucket YOUR_UNIQUE_BUCKET_NAME --region YOUR_REGION --create-bucket-configuration LocationConstraint=YOUR_REGION${NC} (for regions other than us-east-1)"
echo -e "     ${GREEN}aws s3api create-bucket --bucket YOUR_UNIQUE_BUCKET_NAME --region us-east-1${NC} (for us-east-1)"
echo -e "     ${GREEN}aws s3api put-bucket-versioning --bucket YOUR_UNIQUE_BUCKET_NAME --versioning-configuration Status=Enabled${NC}"
echo -e "  2. ${YELLOW}Create a DynamoDB table:${NC} This table will be used for Terraform state locking."
echo -e "     The table MUST have a primary key named 'LockID' (Type: String)."
echo -e "     Example AWS CLI command:"
echo -e "     ${GREEN}aws dynamodb create-table --table-name YOUR_DYNAMODB_TABLE_NAME --attribute-definitions AttributeName=LockID,AttributeType=S --key-schema AttributeName=LockID,KeyType=HASH --provisioned-throughput ReadCapacityUnits=1,WriteCapacityUnits=1 --region YOUR_REGION${NC}"
echo -e "\nAfter creating these resources, you MUST uncomment and fill in the 'backend \"s3\"' block"
echo -e "in the file: ${YELLOW}deploy/aws/terraform/versions.tf${NC}"
echo -e "Example content is provided there. Update 'bucket', 'key', 'region', and 'dynamodb_table' fields."
read -p "Press Enter once you understand this step..."

# 6. AWS Secrets Manager Population
print_header "AWS Secrets Manager - Secret Population"
echo -e "The Terraform scripts will create placeholders for secrets in AWS Secrets Manager."
echo -e "You will need to populate these secrets with their actual values *after* they are created by 'terraform apply'."
echo -e "Here are the secret name ${YELLOW}suffixes${NC} that will be created (prefixed by '\${PROJECT_NAME}/'):"
# This list should match the keys in var.secrets_to_create in secrets_manager.tf / variables.tf
declare -a secret_suffixes=(
    "POSTGRES_USER" "POSTGRES_PASSWORD" "HASURA_GRAPHQL_ADMIN_SECRET" "HASURA_GRAPHQL_JWT_SECRET"
    "TRAEFIK_USER" "TRAEFIK_PASSWORD" "BASIC_AUTH_FUNCTIONS_ADMIN" "OPENAI_API_KEY" "API_TOKEN"
    "GOOGLE_CLIENT_ID_ANDROID" "GOOGLE_CLIENT_ID_IOS" "GOOGLE_CLIENT_ID_WEB" "GOOGLE_CLIENT_ID_ATOMIC_WEB"
    "GOOGLE_CLIENT_SECRET_ATOMIC_WEB" "GOOGLE_CLIENT_SECRET_WEB" "GOOGLE_CALENDAR_ID" "GOOGLE_CALENDAR_CREDENTIALS"
    "GOOGLE_MAP_KEY" "GOOGLE_PLACE_API_KEY" "STORAGE_ACCESS_KEY" "STORAGE_SECRET_KEY" "STORAGE_REGION"
    "KAFKA_USERNAME" "KAFKA_PASSWORD" "OPENSEARCH_USERNAME" "OPENSEARCH_PASSWORD"
    "ZOOM_CLIENT_ID" "ZOOM_CLIENT_SECRET" "ZOOM_PASS_KEY" "ZOOM_SALT_FOR_PASS" "ZOOM_IV_FOR_PASS" "ZOOM_WEBHOOK_SECRET_TOKEN"
    "OPTAPLANNER_USERNAME" "OPTAPLANNER_PASSWORD" "SMTP_HOST" "SMTP_PORT" "SMTP_USER" "SMTP_PASS" "SMTP_FROM_EMAIL"
    "TWILIO_ACCOUNT_SID" "TWILIO_AUTH_TOKEN" "TWILIO_PHONE_NO" "STRIPE_API_KEY" "STRIPE_WEBHOOK_SECRET"
    "ONESIGNAL_APP_ID" "ONESIGNAL_REST_API_KEY" "SLACK_BOT_TOKEN" "SLACK_SIGNING_SECRET" "SLACK_CHANNEL_ID"
    "JWT_SECRET" "ENCRYPTION_KEY" "SESSION_SECRET_KEY"
)
for suffix in "${secret_suffixes[@]}"; do
    echo -e "  - \${PROJECT_NAME}/${suffix}"
done
echo -e "\nExample command to update a secret value (run AFTER 'terraform apply'):"
echo -e "${GREEN}aws secretsmanager update-secret --secret-id \${PROJECT_NAME}/YOUR_SECRET_SUFFIX --secret-string \"YOUR_NEW_SECRET_VALUE\" --region YOUR_REGION${NC}"
echo -e "For JSON secrets (like HASURA_GRAPHQL_JWT_SECRET or GOOGLE_CALENDAR_CREDENTIALS), use --secret-string file://your-secret.json"
read -p "Press Enter once you understand this secret population step..."

# 7. ACM Certificate for Load Balancer (Optional)
print_header "ACM Certificate for Load Balancer (Optional)"
echo -e "If you plan to use an AWS Certificate Manager (ACM) certificate for TLS termination at the Load Balancer"
echo -e "(e.g., for Traefik), you should provision or import a certificate in ACM for your domain name (e.g., *.${DOMAIN_NAME}, ${DOMAIN_NAME})."
echo -e "The ARN of this certificate can then be provided in ${YELLOW}deploy/aws/config.sh${NC} (variable 'ACM_CERTIFICATE_ARN')."
echo -e "Alternatively, Traefik can manage Let's Encrypt certificates internally if you prefer."
read -p "Press Enter to continue..."

# 8. Create config.sh from example if it doesn't exist
CONFIG_FILE="deploy/aws/config.sh"
CONFIG_EXAMPLE_FILE="deploy/aws/config.sh.example"
print_header "Configuration File Setup (config.sh)"
if [ -f "$CONFIG_FILE" ]; then
    echo -e "${GREEN}$CONFIG_FILE already exists.${NC} Please ensure it is up-to-date."
else
    if [ -f "$CONFIG_EXAMPLE_FILE" ]; then
        cp "$CONFIG_EXAMPLE_FILE" "$CONFIG_FILE"
        echo -e "${GREEN}$CONFIG_FILE copied from $CONFIG_EXAMPLE_FILE.${NC}"
        echo -e "${YELLOW}You MUST review and edit $CONFIG_FILE with your specific settings.${NC}"
    else
        echo -e "${RED}Error: $CONFIG_EXAMPLE_FILE not found. Cannot create $CONFIG_FILE.${NC}"
        echo -e "Please restore $CONFIG_EXAMPLE_FILE or create $CONFIG_FILE manually."
    fi
fi

# 9. Instruct user to review and fill config.sh
echo -e "\n${YELLOW}ACTION REQUIRED:${NC} Please open ${YELLOW}$CONFIG_FILE${NC} in a text editor."
echo -e "Review all variables and update them with your specific values for:"
echo -e "  - AWS Region and Profile (if any)"
echo -e "  - Project Name, Environment Name, Domain Name"
echo -e "  - EKS Cluster settings"
echo -e "  - Database settings"
echo -e "  - S3 bucket for CodeBuild artifacts"
echo -e "  - ACM Certificate ARN (optional)"
echo -e "Ensure all placeholder values like 'your-...' or 'example.com' are replaced."

# 10. Completion Message
print_header "Initial Configuration Guidance Complete!"
echo -e "Next steps:"
echo -e "  1. ${YELLOW}Manually create S3 bucket and DynamoDB table for Terraform backend.${NC}"
echo -e "  2. ${YELLOW}Uncomment and update the 'backend \"s3\"' block in 'deploy/aws/terraform/versions.tf'.${NC}"
echo -e "  3. ${YELLOW}Carefully review and update 'deploy/aws/config.sh' with all your specific settings.${NC}"
echo -e "  4. After running 'terraform apply' for the first time, ${YELLOW}populate the actual secret values in AWS Secrets Manager.${NC}"
echo -e "\nOnce these steps are done, you can proceed with running the Terraform and other deployment scripts."
echo -e "Refer to the project's README for detailed deployment instructions."

exit 0
