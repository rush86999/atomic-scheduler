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
    error_exit "$CONFIG_FILE not found. Cannot determine which resources to destroy."
fi

# 2. Critical Pre-flight Checks
print_header "Performing Pre-flight Checks..."
declare -a critical_vars=(
    "AWS_DEFAULT_REGION"
    "PROJECT_NAME"
    "EKS_CLUSTER_NAME"
    # Add other variables that are absolutely essential for identifying resources to destroy
    "CODEBUILD_ARTIFACTS_S3_BUCKET" # Needed by Terraform iam.tf for policy scoping
)
for var_name in "${critical_vars[@]}"; do
    if [ -z "${!var_name}" ]; then # Check if the variable is empty
        error_exit "Critical variable $var_name is not set in $CONFIG_FILE. Please configure it to ensure correct resource targeting for destruction."
    fi
done
# Fetch AWS Account ID if not already set (used in some Terraform resource ARNs for policies)
if [ -z "${AWS_ACCOUNT_ID}" ]; then
    echo "Fetching AWS Account ID..."
    AWS_ACCOUNT_ID=$(aws sts get-caller-identity --query Account --output text)
    if [ -z "${AWS_ACCOUNT_ID}" ]; then
        error_exit "Failed to fetch AWS Account ID. Ensure AWS CLI is configured correctly."
    fi
    export AWS_ACCOUNT_ID # Export it for use in this script session
    echo "AWS Account ID: ${AWS_ACCOUNT_ID}"
fi
echo -e "${GREEN}Critical configuration variables seem to be set.${NC}"

# Confirmation Prompt
print_header "Confirmation Required"
echo -e "${YELLOW}WARNING: This script will attempt to destroy Kubernetes resources managed by Kustomize"
echo -e "and then destroy AWS infrastructure managed by Terraform based on your configuration in:"
echo -e "  - Terraform directory: $TERRAFORM_DIR"
echo -e "  - Kustomize overlay: $KUSTOMIZE_DIR_AWS_OVERLAY"
echo -e "  - Configuration: $CONFIG_FILE"
echo -e "\nThis includes EKS cluster, RDS instance, VPC, S3 buckets (if managed by this Terraform), IAM roles, etc."
echo -e "${RED}This action is IRREVERSIBLE and will lead to data loss if not managed carefully (e.g., RDS final snapshots).${NC}"
echo -e "Ensure you have backed up any critical data."
read -p "Are you absolutely sure you want to proceed? Type 'DESTROY' to confirm: " confirmation
if [ "$confirmation" != "DESTROY" ]; then
    echo -e "${YELLOW}Destruction cancelled by user.${NC}"
    exit 0
fi

# 3. (Optional but Recommended) Delete Kubernetes Resources via Kustomize
print_header "Deleting Kubernetes Resources via Kustomize..."
echo -e "This step attempts to gracefully delete Kubernetes resources (like LoadBalancers, PVCs)"
echo -e "before destroying the underlying cloud infrastructure."

# Configure kubectl first, in case it's not configured or context changed
echo "Attempting to update kubeconfig for cluster: ${EKS_CLUSTER_NAME} in region ${AWS_DEFAULT_REGION}"
if aws eks update-kubeconfig --name "${EKS_CLUSTER_NAME}" --region "${AWS_DEFAULT_REGION}" ${AWS_PROFILE:+--profile "$AWS_PROFILE"}; then
    echo -e "${GREEN}kubectl configured successfully for EKS cluster.${NC}"
else
    echo -e "${YELLOW}Warning: Failed to configure kubectl for EKS cluster. This might be okay if the cluster is already deleted or inaccessible.${NC}"
    echo -e "${YELLOW}Proceeding with Kubernetes resource deletion attempt, but it may fail if cluster is not reachable.${NC}"
fi

# Kustomize image overrides might have been set by deploy.sh.
# For deletion, these overrides are usually not critical but ensuring the kustomization.yaml points to the correct base is.
# We assume the kustomization.yaml in the overlay is correctly configured.
cd "$KUSTOMIZE_DIR_AWS_OVERLAY" || error_exit "Failed to change directory to $KUSTOMIZE_DIR_AWS_OVERLAY"
echo "Attempting to delete Kubernetes resources defined in $KUSTOMIZE_DIR_AWS_OVERLAY..."
if kubectl delete -k . --ignore-not-found=true --timeout=5m; then # Apply from the current directory
    echo -e "${GREEN}Kubernetes resources deletion command executed. Resources may take time to terminate.${NC}"
    echo -e "Waiting for a moment for resources to begin termination..."
    sleep 60 # Wait 1 minute
else
    echo -e "${YELLOW}Warning: 'kubectl delete -k' command failed. Some Kubernetes resources might not be deleted.${NC}"
    echo -e "${YELLOW}This could be due to the cluster already being deleted or other issues. Terraform destroy will proceed.${NC}"
fi
cd "$PROJECT_ROOT_DIR" # Go back to project root or a safe directory

# 4. Destroy Terraform Infrastructure
print_header "Destroying Terraform Infrastructure..."
cd "$TERRAFORM_DIR" || error_exit "Failed to change directory to $TERRAFORM_DIR"

# Prepare Terraform variables for destroy command (similar to apply)
TF_DESTROY_ARGS=("-auto-approve") # Add -auto-approve for CI/CD, remove for manual review
TF_VAR_FILES_DESTROY=() # Add any .tfvars files if used

TF_VARS_DESTROY=(
    "aws_region=${AWS_DEFAULT_REGION}"
    "project_name=${PROJECT_NAME}"
    "environment_name=${ENVIRONMENT_NAME}"
    "eks_cluster_name=${EKS_CLUSTER_NAME}"
    "eks_cluster_version=${EKS_CLUSTER_VERSION:-1.28}"
    "rds_db_name=${RDS_DB_NAME:-atomicdb}"
    "rds_username=${RDS_USERNAME:-atomicadmin}"
    "codebuild_artifacts_s3_bucket_name=${CODEBUILD_ARTIFACTS_S3_BUCKET}"
    "aws_account_id=${AWS_ACCOUNT_ID}"
    # Ensure all variables that affect resource naming or conditional creation are passed
    # For RDS, rds_skip_final_snapshot is important. Ensure it's set in config.sh or TF vars.
    # Example: "rds_skip_final_snapshot=true" (if set in config.sh and TF var is defined)
)
# Add -var flags to TF_DESTROY_ARGS
for tf_var in "${TF_VARS_DESTROY[@]}"; do
    TF_DESTROY_ARGS+=("-var")
    TF_DESTROY_ARGS+=("$tf_var")
done

# Add -var-file flags if any
for tf_var_file in "${TF_VAR_FILES_DESTROY[@]}"; do
    TF_DESTROY_ARGS+=("$tf_var_file")
done

echo "Running: terraform destroy ${TF_DESTROY_ARGS[*]}"
echo -e "${YELLOW}Terraform will now attempt to destroy all managed infrastructure.${NC}"
echo -e "${YELLOW}Please monitor the output carefully.${NC}"
if ! terraform destroy "${TF_DESTROY_ARGS[@]}"; then
    error_exit "Terraform destroy failed. Some resources may still exist."
fi
echo -e "${GREEN}Terraform infrastructure destroyed successfully.${NC}"

# 5. Completion Message
print_header "AWS Destruction Script Finished!"
echo -e "All specified Kubernetes resources (if reachable) and Terraform-managed AWS infrastructure should now be destroyed."
echo -e "Please verify in your AWS console that all resources have been terminated to avoid unexpected charges."
echo -e "Remember to also delete the Terraform S3 backend bucket and DynamoDB table manually if you no longer need them."
cd "$PROJECT_ROOT_DIR" || exit 1
exit 0
