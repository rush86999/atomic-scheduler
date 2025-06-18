#!/bin/bash

# Exit immediately if a command exits with a non-zero status.
set -e
# Treat unset variables as an error when substituting.
# set -u # Can be too strict if config.sh has optional unset vars
# Return value of a pipeline is the value of the last command to exit with a non-zero status,
# or zero if all commands in the pipeline exit successfully.
set -o pipefail

# --- Configuration and Setup ---
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT_DIR="$(cd "${SCRIPT_DIR}/../../.." && pwd)" # Assuming scripts are in deploy/aws/scripts
TERRAFORM_DIR="${PROJECT_ROOT_DIR}/deploy/aws/terraform"
KUSTOMIZE_DIR_AWS_OVERLAY="${PROJECT_ROOT_DIR}/deploy/kubernetes/overlays/aws"
CONFIG_FILE="${SCRIPT_DIR}/../config.sh"

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

# Function to print error message and exit
error_exit() {
    echo -e "${RED}ERROR: $1${NC}" >&2
    exit 1
}

# 1. Source Configuration
print_header "Loading AWS Deployment Configuration..."
if [ -f "$CONFIG_FILE" ]; then
    # shellcheck source=../config.sh
    source "$CONFIG_FILE"
    echo -e "${GREEN}Configuration file $CONFIG_FILE loaded.${NC}"
else
    error_exit "$CONFIG_FILE not found. Please run configure.sh first or create it manually."
fi

# 2. Critical Pre-flight Checks
print_header "Performing Pre-flight Checks..."
declare -a critical_vars=(
    "AWS_DEFAULT_REGION"
    "PROJECT_NAME"
    "ENVIRONMENT_NAME"
    "EKS_CLUSTER_NAME"
    "DOMAIN_NAME" # Needed for constructing various URLs and potentially for ACM certs
    "CODEBUILD_ARTIFACTS_S3_BUCKET" # Needed by Terraform iam.tf
    # Add ECR image name variables that are defined in config.sh if they are critical for placeholders
    "IMAGE_NAME_FUNCTIONS"
    "IMAGE_NAME_APP"
    # RDS_PASSWORD_SECRET_ARN will be an output from Terraform, so not checked here as an input.
)
for var_name in "${critical_vars[@]}"; do
    if [ -z "${!var_name}" ]; then # Check if the variable is empty
        error_exit "Critical variable $var_name is not set in $CONFIG_FILE. Please configure it."
    fi
done
echo -e "${GREEN}Critical configuration variables seem to be set.${NC}"

# Fetch AWS Account ID if not already set in config.sh (e.g. by CODEBUILD_ARTIFACTS_S3_BUCKET logic)
if [ -z "${AWS_ACCOUNT_ID}" ]; then
    echo "Fetching AWS Account ID..."
    AWS_ACCOUNT_ID=$(aws sts get-caller-identity --query Account --output text)
    if [ -z "${AWS_ACCOUNT_ID}" ]; then
        error_exit "Failed to fetch AWS Account ID. Ensure AWS CLI is configured correctly."
    fi
    export AWS_ACCOUNT_ID # Export it for use in this script session
    echo "AWS Account ID: ${AWS_ACCOUNT_ID}"
fi


# 3. (Optional) Build and Push Docker Images
print_header "Docker Image Build and Push (Placeholder)"
echo -e "${YELLOW}This step is typically handled by a CI/CD pipeline.${NC}"
echo -e "For manual deployment, you would need to:"
echo -e "  1. Log in to ECR: ${GREEN}aws ecr get-login-password --region ${AWS_DEFAULT_REGION} | docker login --username AWS --password-stdin ${AWS_ACCOUNT_ID}.dkr.ecr.${AWS_DEFAULT_REGION}.amazonaws.com${NC}"
echo -e "  2. For each custom service (e.g., functions, app, scheduler, handshake, oauth):"
echo -e "     - Navigate to the service's source code directory (e.g., ${PROJECT_ROOT_DIR}/services/functions)."
echo -e "     - Build the Docker image: ${GREEN}docker build -t ${AWS_ACCOUNT_ID}.dkr.ecr.${AWS_DEFAULT_REGION}.amazonaws.com/${PROJECT_NAME}-${IMAGE_NAME_FUNCTIONS}:latest .${NC} (example for functions)"
echo -e "     - Push the image: ${GREEN}docker push ${AWS_ACCOUNT_ID}.dkr.ecr.${AWS_DEFAULT_REGION}.amazonaws.com/${PROJECT_NAME}-${IMAGE_NAME_FUNCTIONS}:latest${NC}"
echo -e "Ensure your images are built and pushed to ECR before proceeding with Kubernetes deployment if not using placeholders."
# read -p "Press [Enter] to acknowledge this step and continue (assuming images are ready or placeholders are used)..."

# 4. Initialize Terraform
print_header "Initializing Terraform..."
cd "$TERRAFORM_DIR" || error_exit "Failed to change directory to $TERRAFORM_DIR"
if terraform init -input=false; then
    echo -e "${GREEN}Terraform initialized successfully.${NC}"
else
    error_exit "Terraform initialization failed."
fi
# Backend configuration should have been handled as per configure.sh guidance.

# 5. Apply Terraform Infrastructure
print_header "Applying Terraform Infrastructure..."
echo -e "This will provision AWS resources (VPC, EKS, RDS, ECR, Secrets Manager, etc.)."
echo -e "Review the plan carefully if prompted and not using -auto-approve."

# Prepare Terraform variables
# Most variables are defined with defaults in variables.tf or sourced from environment by provider.
# Explicitly pass those that are crucial or dynamically constructed from config.sh.
# The `secrets_to_create` map and `custom_image_names` list have defaults in terraform/variables.tf
# and might not need to be passed if defaults are sufficient.
TF_APPLY_ARGS=("-auto-approve") # Add -auto-approve for CI/CD, remove for manual review
TF_VAR_FILES=() # Add any .tfvars files if used, e.g., TF_VAR_FILES+=("-var-file=myenv.tfvars")

# Construct -var arguments from config.sh variables that map to Terraform variables
# Ensure Terraform variable names match those in variables.tf
TF_VARS=(
    "aws_region=${AWS_DEFAULT_REGION}"
    "project_name=${PROJECT_NAME}"
    "environment_name=${ENVIRONMENT_NAME}"
    "eks_cluster_name=${EKS_CLUSTER_NAME}"
    "eks_cluster_version=${EKS_CLUSTER_VERSION:-1.28}" # Use default from config or TF if not set
    "rds_db_name=${RDS_DB_NAME:-atomicdb}"
    "rds_username=${RDS_USERNAME:-atomicadmin}"
    "codebuild_artifacts_s3_bucket_name=${CODEBUILD_ARTIFACTS_S3_BUCKET}"
    "aws_account_id=${AWS_ACCOUNT_ID}" # Pass the fetched or configured account ID
    # public_subnet_cidrs, private_subnet_cidrs, database_subnet_cidrs are expected to be set if not using defaults in TF variables.tf
    # For example: "public_subnet_cidrs=[\"10.0.1.0/24\", \"10.0.2.0/24\"]" (ensure proper list formatting for TF)
)

# Add -var flags to TF_APPLY_ARGS
for tf_var in "${TF_VARS[@]}"; do
    TF_APPLY_ARGS+=("-var")
    TF_APPLY_ARGS+=("$tf_var")
done

# Add -var-file flags if any
for tf_var_file in "${TF_VAR_FILES[@]}"; do
    TF_APPLY_ARGS+=("$tf_var_file")
done

echo "Running: terraform apply ${TF_APPLY_ARGS[*]}"
if ! terraform apply "${TF_APPLY_ARGS[@]}"; then
    error_exit "Terraform apply failed."
fi
echo -e "${GREEN}Terraform infrastructure applied successfully.${NC}"

# Capture Terraform Outputs
print_header "Capturing Terraform Outputs..."
EKS_CLUSTER_NAME_TF_OUT=$(terraform output -raw eks_cluster_name_output || echo "")
EKS_ENDPOINT_TF_OUT=$(terraform output -raw eks_cluster_endpoint_output || echo "")
EKS_CA_DATA_TF_OUT=$(terraform output -raw eks_cluster_certificate_authority_data_output || echo "")
EKS_OIDC_PROVIDER_URL_NO_PREFIX_TF_OUT=$(terraform output -raw eks_oidc_provider_url_no_prefix_output || echo "")
RDS_PASSWORD_SECRET_ARN_TF_OUT=$(terraform output -raw rds_password_secret_arn_output || echo "")
# Add more outputs as needed, e.g., ECR URLs if you want to verify them here

# Check if critical outputs were captured (example)
if [ -z "$EKS_CLUSTER_NAME_TF_OUT" ]; then
    echo -e "${YELLOW}Warning: Could not capture EKS_CLUSTER_NAME_TF_OUT from Terraform outputs. Using value from config.sh: ${EKS_CLUSTER_NAME}${NC}"
    EKS_CLUSTER_NAME_TF_OUT="${EKS_CLUSTER_NAME}" # Fallback to config.sh var
fi


# 6. Configure kubectl
print_header "Configuring kubectl for EKS Cluster..."
echo "Attempting to update kubeconfig for cluster: ${EKS_CLUSTER_NAME_TF_OUT} in region ${AWS_DEFAULT_REGION}"
if aws eks update-kubeconfig --name "${EKS_CLUSTER_NAME_TF_OUT}" --region "${AWS_DEFAULT_REGION}" ${AWS_PROFILE:+--profile "$AWS_PROFILE"}; then
    echo -e "${GREEN}kubectl configured successfully for EKS cluster.${NC}"
    echo "Current kubectl context:"
    kubectl config current-context
else
    error_exit "Failed to configure kubectl for EKS cluster. Check AWS credentials and EKS cluster status."
fi

# 7. (Important Pause) Guide Secret Population
print_header "ACTION REQUIRED: Populate Secrets in AWS Secrets Manager"
echo -e "${YELLOW}Terraform has created SECRET PLACEHOLDERS in AWS Secrets Manager.${NC}"
echo -e "You MUST NOW MANUALLY POPULATE these secrets with their actual values."
echo -e "Refer to the list output by 'configure.sh' or check your AWS Secrets Manager console for secrets prefixed with '${PROJECT_NAME}/'."
echo -e "Example: For '${PROJECT_NAME}/POSTGRES_PASSWORD', its ARN is '${RDS_PASSWORD_SECRET_ARN_TF_OUT}' (if available)."
echo -e "Use the AWS Management Console or AWS CLI to update these secret values."
echo -e "Example CLI: ${GREEN}aws secretsmanager update-secret --secret-id \"${PROJECT_NAME}/YOUR_SECRET_SUFFIX\" --secret-string \"YOUR_ACTUAL_SECRET_VALUE\" --region ${AWS_DEFAULT_REGION}${NC}"
read -p "Press [Enter] to continue AFTER all necessary secrets in AWS Secrets Manager have been populated with actual values..."

# 8. Deploy Kubernetes Manifests using Kustomize
print_header "Deploying Kubernetes Manifests with Kustomize..."
cd "$KUSTOMIZE_DIR_AWS_OVERLAY" || error_exit "Failed to change directory to $KUSTOMIZE_DIR_AWS_OVERLAY"

# Image Overrides using kustomize edit set image
# These placeholders should match those in deploy/kubernetes/overlays/aws/kustomization.yaml 'images' section
# The ECR repository name format in TF was: ${var.project_name}-${each.key}
# So, for project_name="atomic" and image_name="atomic-functions", repo name is "atomic-atomic-functions"
# Ensure PROJECT_NAME_FROM_TF in kustomization.yaml matches this script's PROJECT_NAME.
# The image tag (e.g., 'latest' or a Git SHA) should be consistent with what was pushed.
IMAGE_TAG="${IMAGE_TAG:-latest}" # Use environment variable IMAGE_TAG or default to 'latest'

declare -A image_map=(
    ["placeholder-atomic-scheduler"]="${PROJECT_NAME}-${IMAGE_NAME_SCHEDULER}"
    ["placeholder-atomic-functions"]="${PROJECT_NAME}-${IMAGE_NAME_FUNCTIONS}"
    ["placeholder-atomic-handshake"]="${PROJECT_NAME}-${IMAGE_NAME_HANDSHAKE}"
    ["placeholder-atomic-oauth"]="${PROJECT_NAME}-${IMAGE_NAME_OAUTH}"
    ["placeholder-atomic-app"]="${PROJECT_NAME}-${IMAGE_NAME_APP}"
)

echo "Updating Kustomize image overrides..."
for placeholder_name in "${!image_map[@]}"; do
    ecr_repo_name="${image_map[$placeholder_name]}"
    actual_image_uri="${AWS_ACCOUNT_ID}.dkr.ecr.${AWS_DEFAULT_REGION}.amazonaws.com/${ecr_repo_name}:${IMAGE_TAG}"
    echo "Setting image: ${placeholder_name} -> ${actual_image_uri}"
    if kustomize edit set image "${placeholder_name}=${actual_image_uri}"; then
        echo -e "${GREEN}Successfully set image for ${placeholder_name}.${NC}"
    else
        error_exit "Failed to set image for ${placeholder_name} using kustomize edit."
    fi
done
echo -e "${GREEN}Kustomize image overrides updated.${NC}"

# Apply Kustomize manifests
echo "Applying Kustomized Kubernetes manifests..."
if kubectl apply -k .; then # Apply from the current directory (KUSTOMIZE_DIR_AWS_OVERLAY)
    echo -e "${GREEN}Kubernetes manifests applied successfully.${NC}"
else
    error_exit "Failed to apply Kubernetes manifests."
fi

# 9. (Optional) Wait for Load Balancer and Output URLs
print_header "Finalizing Deployment (Optional Steps)"
echo -e "Kubernetes services are being provisioned. This may take several minutes."
echo -e "You may need to wait for External IP addresses to be assigned to LoadBalancer services (e.g., Traefik)."
echo -e "You can monitor with: ${GREEN}kubectl get svc --all-namespaces -w${NC}"
# Add logic here to fetch Traefik LB IP/hostname and construct application URLs if desired.
# Example:
# TRAEFIK_LB_HOSTNAME=""
# MAX_RETRIES=30
# RETRY_INTERVAL=10
# for i in $(seq 1 $MAX_RETRIES); do
#     TRAEFIK_LB_HOSTNAME=$(kubectl get svc traefik -n traefik-ingress -o jsonpath='{.status.loadBalancer.ingress[0].hostname}' 2>/dev/null || \
#                           kubectl get svc traefik -n traefik-ingress -o jsonpath='{.status.loadBalancer.ingress[0].ip}' 2>/dev/null || echo "")
#     if [ -n "$TRAEFIK_LB_HOSTNAME" ]; then
#         echo -e "${GREEN}Traefik LoadBalancer provisioned: ${TRAEFIK_LB_HOSTNAME}${NC}"
#         break
#     fi
#     echo "Waiting for Traefik LoadBalancer IP/hostname... (Attempt $i/$MAX_RETRIES)"
#     sleep $RETRY_INTERVAL
# done
# if [ -z "$TRAEFIK_LB_HOSTNAME" ]; then
#     echo -e "${YELLOW}Warning: Traefik LoadBalancer IP/hostname not found after $MAX_RETRIES attempts.${NC}"
# else
#     echo -e "${GREEN}Application should be accessible at: https://${DOMAIN_NAME} (ensure DNS is configured to point to ${TRAEFIK_LB_HOSTNAME})${NC}"
# fi

# 10. Completion Message
print_header "AWS Deployment Script Finished!"
echo -e "Please check the status of your EKS cluster, RDS instance, and Kubernetes pods."
echo -e "Remember to configure DNS records for your domain (${DOMAIN_NAME}) to point to the provisioned LoadBalancer external IP/hostname."
cd "$PROJECT_ROOT_DIR" || exit 1 # Return to project root
exit 0
