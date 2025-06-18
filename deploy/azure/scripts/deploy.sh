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
RESOURCES_DIR_AZURE_OVERLAY="${KUSTOMIZE_DIR_AZURE_OVERLAY}/resources" # For SecretProviderClass
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
    error_exit "$CONFIG_FILE not found. Please run configure.sh first or create it manually."
fi

# 2. Critical Pre-flight Checks
print_header "Performing Pre-flight Checks..."
declare -a critical_vars=(
    "AZURE_REGION"
    "RESOURCE_GROUP_NAME"
    "PROJECT_NAME"
    "ENVIRONMENT_NAME"
    "AKS_CLUSTER_NAME_SUFFIX" # Used to construct full AKS cluster name
    "DOMAIN_NAME"
    "AZURE_TENANT_ID" # Needed for SecretProviderClass
    # ACR_NAME_SUFFIX, KEY_VAULT_NAME_SUFFIX, PG_SERVER_NAME_SUFFIX are used by TF to construct names
    "ACR_NAME_SUFFIX"
    "KEY_VAULT_NAME_SUFFIX"
    "PG_SERVER_NAME_SUFFIX"
    # Image names from config.sh
    "IMAGE_NAME_FUNCTIONS"
    "IMAGE_NAME_APP"
)
for var_name in "${critical_vars[@]}"; do
    if [ -z "${!var_name}" ]; then # Check if the variable is empty
        error_exit "Critical variable $var_name is not set in $CONFIG_FILE. Please configure it."
    fi
done
echo -e "${GREEN}Critical configuration variables seem to be set.${NC}"

# Construct full resource names that will be output by Terraform (and used by K8s)
# These depend on Terraform's local.final_..._name logic which might include random strings.
# For now, we'll rely on Terraform outputting the exact names.
# We will need KEY_VAULT_NAME_TF_OUT and ACR_LOGIN_SERVER_TF_OUT from Terraform.


# 3. (Optional) Build and Push Docker Images to ACR
print_header "Docker Image Build and Push to ACR (Placeholder)"
echo -e "${YELLOW}This step is typically handled by a CI/CD pipeline.${NC}"
echo -e "For manual deployment, you would need to:"
echo -e "  1. Log in to ACR: ${GREEN}az acr login --name YOUR_ACR_NAME${NC} (e.g., ${PROJECT_NAME}${ACR_NAME_SUFFIX} - name needs sanitization from TF)"
echo -e "     (The actual ACR login server will be an output from Terraform: \${ACR_LOGIN_SERVER_TF_OUT})"
echo -e "  2. For each custom service (e.g., functions, app, scheduler, handshake, oauth):"
echo -e "     - Navigate to the service's source code directory (e.g., ${PROJECT_ROOT_DIR}/services/functions)."
echo -e "     - Build the Docker image: ${GREEN}docker build -t \${ACR_LOGIN_SERVER_TF_OUT}/${PROJECT_NAME}-${IMAGE_NAME_FUNCTIONS}:latest .${NC} (example for functions)"
echo -e "     - Push the image: ${GREEN}docker push \${ACR_LOGIN_SERVER_TF_OUT}/${PROJECT_NAME}-${IMAGE_NAME_FUNCTIONS}:latest${NC}"
echo -e "Ensure your images are built and pushed to ACR before proceeding with Kubernetes deployment if not using placeholders."
# read -p "Press [Enter] to acknowledge this step and continue (assuming images are ready or placeholders are used)..."

# 4. Initialize & Apply Terraform
print_header "Initializing Terraform for Azure..."
cd "$TERRAFORM_DIR" || error_exit "Failed to change directory to $TERRAFORM_DIR"
if terraform init -input=false; then
    echo -e "${GREEN}Terraform initialized successfully.${NC}"
else
    error_exit "Terraform initialization failed."
fi

print_header "Applying Terraform Infrastructure for Azure..."
echo -e "This will provision Azure resources (VNet, AKS, PostgreSQL, ACR, Key Vault, etc.)."

TF_APPLY_ARGS_AZURE=("-auto-approve") # Add -auto-approve for CI/CD
TF_VAR_FILES_AZURE=()

TF_VARS_AZURE=(
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
    # Add other vars from config.sh that are defined in terraform/variables.tf
)

for tf_var in "${TF_VARS_AZURE[@]}"; do
    TF_APPLY_ARGS_AZURE+=("-var")
    TF_APPLY_ARGS_AZURE+=("$tf_var")
done

for tf_var_file in "${TF_VAR_FILES_AZURE[@]}"; do
    TF_APPLY_ARGS_AZURE+=("$tf_var_file")
done

echo "Running: terraform apply ${TF_APPLY_ARGS_AZURE[*]}"
if ! terraform apply "${TF_APPLY_ARGS_AZURE[@]}"; then
    error_exit "Terraform apply failed for Azure."
fi
echo -e "${GREEN}Terraform Azure infrastructure applied successfully.${NC}"

# Capture Terraform Outputs
print_header "Capturing Azure Terraform Outputs..."
AKS_CLUSTER_NAME_TF_OUT=$(terraform output -raw aks_cluster_name_output || echo "")
AKS_RESOURCE_GROUP_TF_OUT=$(terraform output -raw aks_node_resource_group_output || echo "${RESOURCE_GROUP_NAME}") # AKS nodes are in a separate RG or same if specified
KEY_VAULT_NAME_TF_OUT=$(terraform output -raw key_vault_name_output || echo "") # Assuming key_vault.tf has 'key_vault_name_output'
ACR_LOGIN_SERVER_TF_OUT=$(terraform output -raw acr_login_server_output || echo "")
# TENANT_ID is already in $AZURE_TENANT_ID from config.sh

if [ -z "$AKS_CLUSTER_NAME_TF_OUT" ]; then
    # Construct from config if output fails, though TF apply success should mean output is available
    AKS_CLUSTER_NAME_TF_OUT="${PROJECT_NAME}-aks-${AKS_CLUSTER_NAME_SUFFIX}-${ENVIRONMENT_NAME}"
    echo -e "${YELLOW}Warning: Could not capture AKS_CLUSTER_NAME_TF_OUT from Terraform. Using constructed: ${AKS_CLUSTER_NAME_TF_OUT}${NC}"
fi
if [ -z "$KEY_VAULT_NAME_TF_OUT" ]; then
    # This is more complex due to sanitization and random string in TF.
    # It's CRITICAL that KEY_VAULT_NAME_TF_OUT is correctly captured or set.
    error_exit "Failed to capture KEY_VAULT_NAME_TF_OUT from Terraform outputs. This is critical for SecretProviderClass."
fi
if [ -z "$ACR_LOGIN_SERVER_TF_OUT" ]; then
    error_exit "Failed to capture ACR_LOGIN_SERVER_TF_OUT from Terraform outputs. This is critical for image overrides."
fi


# 5. Configure kubectl for AKS
print_header "Configuring kubectl for AKS Cluster..."
echo "Attempting to get credentials for cluster: ${AKS_CLUSTER_NAME_TF_OUT} in resource group ${AKS_RESOURCE_GROUP_TF_OUT}"
if az aks get-credentials --name "${AKS_CLUSTER_NAME_TF_OUT}" --resource-group "${AKS_RESOURCE_GROUP_TF_OUT}" --overwrite-existing ${AZURE_SUBSCRIPTION_ID:+--subscription "$AZURE_SUBSCRIPTION_ID"}; then
    echo -e "${GREEN}kubectl configured successfully for AKS cluster.${NC}"
    echo "Current kubectl context:"
    kubectl config current-context
else
    error_exit "Failed to configure kubectl for AKS cluster. Check Azure credentials and AKS cluster status."
fi

# 6. (Important Pause) Guide Secret Population in Azure Key Vault
print_header "ACTION REQUIRED: Populate Secrets in Azure Key Vault"
echo -e "${YELLOW}Terraform has created SECRET PLACEHOLDERS in Azure Key Vault: ${KEY_VAULT_NAME_TF_OUT}.${NC}"
echo -e "You MUST NOW MANUALLY POPULATE these secrets with their actual values."
echo -e "Refer to the list output by 'configure.sh' or check your Azure Key Vault in the portal."
echo -e "Example Azure CLI: ${GREEN}az keyvault secret set --vault-name \"${KEY_VAULT_NAME_TF_OUT}\" --name \"YOUR-SECRET-NAME\" --value \"YOUR_ACTUAL_SECRET_VALUE\"${NC}"
read -p "Press [Enter] to continue AFTER all necessary secrets in Azure Key Vault have been populated..."

# 7. Prepare and Deploy Kubernetes Manifests (Kustomize)
print_header "Deploying Kubernetes Manifests with Kustomize for Azure..."
cd "$KUSTOMIZE_DIR_AZURE_OVERLAY" || error_exit "Failed to change directory to $KUSTOMIZE_DIR_AZURE_OVERLAY"

# Substitute placeholders in SecretProviderClass YAML
SPC_FILE_TEMPLATE="${RESOURCES_DIR_AZURE_OVERLAY}/secret-provider-classes-azure.yaml"
SPC_FILE_TEMP="${RESOURCES_DIR_AZURE_OVERLAY}/secret-provider-classes-azure-processed.yaml"

echo "Substituting placeholders in SecretProviderClass YAML..."
echo "  KEY_VAULT_NAME: ${KEY_VAULT_NAME_TF_OUT}"
echo "  TENANT_ID: ${AZURE_TENANT_ID}"

# Create a copy to modify
cp "$SPC_FILE_TEMPLATE" "$SPC_FILE_TEMP"

# Use sed for substitution (ensure compatibility across sed versions if possible, or use perl/awk for more robust replacement)
# This replaces literal strings like "${KEY_VAULT_NAME}"
sed -i.bak "s|\${KEY_VAULT_NAME}|${KEY_VAULT_NAME_TF_OUT}|g" "$SPC_FILE_TEMP"
sed -i.bak "s|\${TENANT_ID}|${AZURE_TENANT_ID}|g" "$SPC_FILE_TEMP"
rm -f "${SPC_FILE_TEMP}.bak" # Clean up sed backup file

echo -e "${GREEN}SecretProviderClass YAML prepared: ${SPC_FILE_TEMP}${NC}"
# Note: The kustomization.yaml for the Azure overlay should reference this processed file, or this script
# should temporarily rename the original, apply, then revert.
# For simplicity, this script assumes kustomization.yaml references the original name,
# so we will rename the processed file to the original name for kustomize build.
# A cleaner way is to have kustomization.yaml point to the -processed.yaml, or use kustomize vars.
# For this script, let's use the processed file directly if kustomization.yaml can be adapted,
# or overwrite the original with the processed one. Overwriting is simpler for this script's scope.
mv "$SPC_FILE_TEMP" "$SPC_FILE_TEMPLATE"
echo "Original SecretProviderClass YAML updated with dynamic values."


# Image Overrides using kustomize edit set image
IMAGE_TAG="${IMAGE_TAG:-latest}" # Use environment variable IMAGE_TAG or default to 'latest'

declare -A image_map_azure=(
    ["placeholder-atomic-scheduler"]="${PROJECT_NAME}-${IMAGE_NAME_SCHEDULER}"
    ["placeholder-atomic-functions"]="${PROJECT_NAME}-${IMAGE_NAME_FUNCTIONS}"
    ["placeholder-atomic-handshake"]="${PROJECT_NAME}-${IMAGE_NAME_HANDSHAKE}"
    ["placeholder-atomic-oauth"]="${PROJECT_NAME}-${IMAGE_NAME_OAUTH}"
    ["placeholder-atomic-app"]="${PROJECT_NAME}-${IMAGE_NAME_APP}"
)

echo "Updating Kustomize image overrides for ACR..."
for placeholder_name in "${!image_map_azure[@]}"; do
    acr_repo_name="${image_map_azure[$placeholder_name]}" # This is the repo name like 'atomic-atomic-functions'
    actual_image_uri="${ACR_LOGIN_SERVER_TF_OUT}/${acr_repo_name}:${IMAGE_TAG}" # ACR images can be at root of login server + repo name
    echo "Setting image: ${placeholder_name} -> ${actual_image_uri}"
    if kustomize edit set image "${placeholder_name}=${actual_image_uri}"; then
        echo -e "${GREEN}Successfully set image for ${placeholder_name}.${NC}"
    else
        error_exit "Failed to set image for ${placeholder_name} using kustomize edit."
    fi
done
echo -e "${GREEN}Kustomize image overrides updated for ACR.${NC}"

# Apply Kustomize manifests
echo "Applying Kustomized Kubernetes manifests for Azure..."
if kubectl apply -k .; then # Apply from the current directory (KUSTOMIZE_DIR_AZURE_OVERLAY)
    echo -e "${GREEN}Kubernetes manifests applied successfully for Azure.${NC}"
else
    # Revert SPC file if apply fails, to not leave processed file as original
    # This is a simple revert; more robust would be to copy original back.
    # For now, user would need to restore from git if this part fails.
    # mv "$SPC_FILE_TEMPLATE.original" "$SPC_FILE_TEMPLATE" # If we made a .original backup
    error_exit "Failed to apply Kubernetes manifests for Azure."
fi


# 8. (Optional) Wait for Load Balancer and Output URLs
print_header "Finalizing Azure Deployment (Optional Steps)"
echo -e "Kubernetes services are being provisioned. This may take several minutes."
echo -e "You may need to wait for an External IP to be assigned to the Traefik LoadBalancer service."
echo -e "You can monitor with: ${GREEN}kubectl get svc traefik -n traefik-ingress -w${NC}"

# 9. Completion Message
print_header "Azure Deployment Script Finished!"
echo -e "Please check the status of your AKS cluster, PostgreSQL server, and Kubernetes pods."
echo -e "Remember to configure DNS records for your domain (${DOMAIN_NAME}) to point to the Traefik LoadBalancer external IP."
cd "$PROJECT_ROOT_DIR" || exit 1 # Return to project root
exit 0
