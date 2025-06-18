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
PROJECT_ROOT_DIR="$(cd "${SCRIPT_DIR}/../../.." && pwd)" # Assuming scripts are in deploy/azure/scripts
TERRAFORM_DIR="${PROJECT_ROOT_DIR}/deploy/azure/terraform"
KUSTOMIZE_DIR_AZURE_OVERLAY="${PROJECT_ROOT_DIR}/deploy/kubernetes/overlays/azure"
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
print_header "Loading Azure Deployment Configuration..."
if [ -f "$CONFIG_FILE" ]; then
    # shellcheck source=../config.sh
    source "$CONFIG_FILE"
    echo -e "${GREEN}Configuration file $CONFIG_FILE loaded.${NC}"
else
    error_exit "$CONFIG_FILE not found. Cannot determine which resources to destroy."
fi

# 2. Critical Pre-flight Checks
print_header "Performing Pre-flight Checks..."
declare -a critical_vars_destroy=(
    "AZURE_REGION"
    "RESOURCE_GROUP_NAME" # Crucial for targeting the right resources for deletion.
    "PROJECT_NAME"
    "AKS_CLUSTER_NAME_SUFFIX" # Used to construct full AKS cluster name
    # Add other variables that are absolutely essential for identifying resources to destroy
    # For example, if Terraform resources are named using these:
    "ACR_NAME_SUFFIX"
    "KEY_VAULT_NAME_SUFFIX"
    "PG_SERVER_NAME_SUFFIX"
)
for var_name in "${critical_vars_destroy[@]}"; do
    if [ -z "${!var_name}" ]; then # Check if the variable is empty
        error_exit "Critical variable $var_name is not set in $CONFIG_FILE. Please configure it to ensure correct resource targeting for destruction."
    fi
done
echo -e "${GREEN}Critical configuration variables seem to be set for destruction script.${NC}"

# Construct expected AKS cluster name (as Terraform would) to attempt kubectl config
EXPECTED_AKS_CLUSTER_NAME="${PROJECT_NAME}-aks-${AKS_CLUSTER_NAME_SUFFIX}-${ENVIRONMENT_NAME}" # Adjust if TF naming is different


# 3. User Confirmation
print_header "Confirmation Required for Azure Resource Destruction"
echo -e "${YELLOW}WARNING: This script will attempt to destroy Kubernetes resources managed by Kustomize"
echo -e "and then destroy Azure infrastructure managed by Terraform based on your configuration in:"
echo -e "  - Terraform directory: $TERRAFORM_DIR"
echo -e "  - Kustomize overlay: $KUSTOMIZE_DIR_AZURE_OVERLAY"
echo -e "  - Configuration: $CONFIG_FILE (targeting Resource Group: ${RESOURCE_GROUP_NAME})"
echo -e "\nThis includes AKS cluster, PostgreSQL server, ACR, Key Vault, VNet, etc., within the specified Resource Group."
echo -e "${RED}This action is IRREVERSIBLE and will lead to data loss if not managed carefully (e.g., PostgreSQL final backups).${NC}"
echo -e "Ensure you have backed up any critical data."
read -p "Are you absolutely sure you want to proceed? Type 'DESTROY AZURE' to confirm: " confirmation
if [ "$confirmation" != "DESTROY AZURE" ]; then
    echo -e "${YELLOW}Azure destruction cancelled by user.${NC}"
    exit 0
fi

# 4. (Optional but Recommended) Delete Kubernetes Resources via Kustomize
print_header "Deleting Kubernetes Resources via Kustomize for Azure..."
echo -e "This step attempts to gracefully delete Kubernetes resources (like LoadBalancers, PVCs)"
echo -e "before destroying the underlying cloud infrastructure."

# Configure kubectl first
echo "Attempting to configure kubectl for cluster: ${EXPECTED_AKS_CLUSTER_NAME} in resource group ${RESOURCE_GROUP_NAME}"
if az aks get-credentials --name "${EXPECTED_AKS_CLUSTER_NAME}" --resource-group "${RESOURCE_GROUP_NAME}" --overwrite-existing ${AZURE_SUBSCRIPTION_ID:+--subscription "$AZURE_SUBSCRIPTION_ID"}; then
    echo -e "${GREEN}kubectl configured successfully for AKS cluster.${NC}"

    cd "$KUSTOMIZE_DIR_AZURE_OVERLAY" || error_exit "Failed to change directory to $KUSTOMIZE_DIR_AZURE_OVERLAY"
    echo "Attempting to delete Kubernetes resources defined in $KUSTOMIZE_DIR_AZURE_OVERLAY..."
    # Note: The SecretProviderClass YAML might have placeholders if deploy.sh didn't complete or was skipped.
    # `kubectl delete` should ideally handle this gracefully or with `ignore-not-found`.
    # We assume the `kustomization.yaml` points to the original SPC file, not a processed one for deletion.
    if kubectl delete -k . --ignore-not-found=true --timeout=5m; then
        echo -e "${GREEN}Kubernetes resources deletion command executed. Resources may take time to terminate.${NC}"
        echo -e "Waiting for a moment for resources to begin termination..."
        sleep 60 # Wait 1 minute
    else
        echo -e "${YELLOW}Warning: 'kubectl delete -k' command failed. Some Kubernetes resources might not be deleted.${NC}"
        echo -e "${YELLOW}This could be due to the cluster already being partially deleted or other issues. Terraform destroy will proceed.${NC}"
    fi
    cd "$PROJECT_ROOT_DIR" # Go back to project root
else
    echo -e "${YELLOW}Warning: Failed to configure kubectl for AKS cluster ${EXPECTED_AKS_CLUSTER_NAME}.${NC}"
    echo -e "${YELLOW}This might be okay if the cluster is already deleted. Proceeding with Terraform destroy.${NC}"
fi


# 5. Destroy Terraform Infrastructure
print_header "Destroying Terraform Infrastructure for Azure..."
cd "$TERRAFORM_DIR" || error_exit "Failed to change directory to $TERRAFORM_DIR"

# Prepare Terraform variables for destroy command (similar to apply)
TF_DESTROY_ARGS_AZURE=("-auto-approve") # Add -auto-approve for CI/CD
TF_VAR_FILES_DESTROY_AZURE=()

TF_VARS_DESTROY_AZURE=(
    "azure_region=${AZURE_REGION}"
    "resource_group_name=${RESOURCE_GROUP_NAME}"
    "project_name=${PROJECT_NAME}"
    "environment_name=${ENVIRONMENT_NAME}"
    "tenant_id=${AZURE_TENANT_ID}"
    "aks_cluster_name_suffix=${AKS_CLUSTER_NAME_SUFFIX}"
    "aks_kubernetes_version=${AKS_KUBERNETES_VERSION}"
    "log_analytics_workspace_id=${LOG_ANALYTICS_WORKSPACE_ID}"
    "acr_name_suffix=${ACR_NAME_SUFFIX}"
    "key_vault_name_suffix=${KEY_VAULT_NAME_SUFFIX}"
    "pg_server_name_suffix=${PG_SERVER_NAME_SUFFIX}"
    "pg_admin_username=${PG_ADMIN_USERNAME}"
    "pg_initial_database_name=${PG_INITIAL_DATABASE_NAME}"
    # Ensure all variables that affect resource naming or conditional creation are passed.
    # For PostgreSQL, pg_skip_final_backup (if such var exists in TF) would be important.
    # The current postgresql.tf doesn't have a skip_final_backup variable, it's default Azure behavior.
)
for tf_var in "${TF_VARS_DESTROY_AZURE[@]}"; do
    TF_DESTROY_ARGS_AZURE+=("-var")
    TF_DESTROY_ARGS_AZURE+=("$tf_var")
done

for tf_var_file in "${TF_VAR_FILES_DESTROY_AZURE[@]}"; do
    TF_DESTROY_ARGS_AZURE+=("$tf_var_file")
done

echo "Running: terraform destroy ${TF_DESTROY_ARGS_AZURE[*]}"
echo -e "${YELLOW}Terraform will now attempt to destroy all managed Azure infrastructure in Resource Group: ${RESOURCE_GROUP_NAME}.${NC}"
echo -e "${YELLOW}Please monitor the output carefully.${NC}"
if ! terraform destroy "${TF_DESTROY_ARGS_AZURE[@]}"; then
    error_exit "Terraform destroy failed for Azure. Some resources may still exist in ${RESOURCE_GROUP_NAME}."
fi
echo -e "${GREEN}Terraform Azure infrastructure destroyed successfully.${NC}"

# 6. Completion Message
print_header "Azure Destruction Script Finished!"
echo -e "All specified Kubernetes resources (if cluster was reachable) and Terraform-managed Azure infrastructure should now be destroyed."
echo -e "Please verify in your Azure portal that all resources within Resource Group '${RESOURCE_GROUP_NAME}' (if managed by this TF) or specific resources have been terminated to avoid unexpected charges."
echo -e "Remember to also delete the Terraform Azure Blob Storage container and potentially the Storage Account and Resource Group used for the backend manually if you no longer need them."
cd "$PROJECT_ROOT_DIR" || exit 1
exit 0
