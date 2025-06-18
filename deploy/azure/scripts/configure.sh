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
print_header "Welcome to the Atomic Stack Azure Deployment Configuration Script!"
echo -e "This script will guide you through the initial setup and configuration steps for Azure."
echo -e "It will not store any sensitive data but will help you prepare your environment."

# 2. Check for Prerequisites
print_header "Checking Prerequisites..."
declare -a prereqs=("az" "terraform" "kubectl" "kustomize" "jq")
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

# 3. Azure CLI Login and Subscription
print_header "Azure CLI Login and Subscription"
echo -e "Please ensure you are logged into the correct Azure account and have selected the desired subscription."
if ! az account show > /dev/null 2>&1; then
    echo -e "${YELLOW}You are not currently logged into Azure CLI. Attempting login...${NC}"
    az login --use-device-code || error_exit "Azure login failed. Please log in manually and re-run."
else
    CURRENT_USER=$(az account show --query "user.name" -o tsv)
    CURRENT_SUB=$(az account show --query "name" -o tsv)
    CURRENT_SUB_ID=$(az account show --query "id" -o tsv)
    echo -e "${GREEN}Currently logged in as: $CURRENT_USER${NC}"
    echo -e "${GREEN}Current subscription: $CURRENT_SUB (ID: $CURRENT_SUB_ID)${NC}"
fi
echo -e "If this is not the correct account or subscription, please use 'az login' and 'az account set --subscription <SUBSCRIPTION_ID>'."
read -p "Press Enter if your Azure CLI context is correctly set, or Ctrl+C to exit and set it now..."

# 4. Configuration File Guidance
print_header "Configuration File Setup (config.sh)"
echo -e "This deployment uses a configuration file: deploy/azure/config.sh"
echo -e "We will create a default 'config.sh' from 'config.sh.example' if it doesn't exist."
echo -e "You will need to review and edit this file with your specific settings."
echo -e "Key parameters to configure in 'config.sh':"
echo -e "  - ${YELLOW}AZURE_SUBSCRIPTION_ID${NC} and ${YELLOW}AZURE_TENANT_ID${NC}"
echo -e "  - ${YELLOW}AZURE_REGION${NC} (e.g., EastUS, WestEurope)"
echo -e "  - ${YELLOW}RESOURCE_GROUP_NAME${NC} (where resources will be deployed or found)"
echo -e "  - ${YELLOW}PROJECT_NAME${NC} (e.g., atomic, myapp - for resource prefixing)"
echo -e "  - ${YELLOW}ENVIRONMENT_NAME${NC} (e.g., dev, staging, prod)"
echo -e "  - ${YELLOW}DOMAIN_NAME${NC} (e.g., example.com - for application URLs)"
echo -e "  - ${YELLOW}AKS_CLUSTER_NAME_SUFFIX${NC}, ${YELLOW}AKS_KUBERNETES_VERSION${NC}"
echo -e "  - ${YELLOW}ACR_NAME_SUFFIX${NC}, ${YELLOW}KEY_VAULT_NAME_SUFFIX${NC}, ${YELLOW}PG_SERVER_NAME_SUFFIX${NC}"
echo -e "  - ${YELLOW}PG_ADMIN_USERNAME${NC}, ${YELLOW}PG_INITIAL_DATABASE_NAME${NC}"
echo -e "  - ${YELLOW}LOG_ANALYTICS_WORKSPACE_ID${NC} (optional)"
read -p "Press Enter to acknowledge and continue..."

# 5. Terraform Azure Blob Backend Configuration
print_header "Terraform Azure Blob Backend Configuration"
echo -e "${YELLOW}IMPORTANT:${NC} For any serious use of Terraform, a remote backend is crucial."
echo -e "We recommend using Azure Blob Storage for state files."
echo -e "You will need to perform these steps manually in your Azure account:"
echo -e "  1. ${YELLOW}Create a Resource Group (if not using an existing one for state):${NC}"
echo -e "     ${GREEN}az group create --name YOUR_TF_STATE_RG_NAME --location YOUR_REGION${NC}"
echo -e "  2. ${YELLOW}Create a Storage Account:${NC} This account will hold your Terraform state."
echo -e "     (Name must be globally unique, 3-24 lowercase alphanumeric characters)."
echo -e "     ${GREEN}az storage account create --name YOUR_UNIQUE_STORAGE_ACCOUNT_NAME --resource-group YOUR_TF_STATE_RG_NAME --location YOUR_REGION --sku Standard_LRS --encryption-services blob${NC}"
echo -e "  3. ${YELLOW}Create a Blob Container:${NC} This container within the storage account will store the state file."
echo -e "     ${GREEN}az storage container create --name tfstate --account-name YOUR_UNIQUE_STORAGE_ACCOUNT_NAME --auth-mode login${NC}"
echo -e "\nAfter creating these resources, you MUST uncomment and fill in the 'backend \"azurerm\"' block"
echo -e "in the file: ${YELLOW}deploy/azure/terraform/versions.tf${NC}"
echo -e "Update 'resource_group_name', 'storage_account_name', 'container_name', and 'key' fields."
read -p "Press Enter once you understand this step..."

# 6. Azure Key Vault Secret Population
print_header "Azure Key Vault - Secret Population"
echo -e "The Terraform scripts will create placeholders for secrets in Azure Key Vault."
echo -e "You will need to populate these secrets with their actual values *after* they are created by 'terraform apply'."
echo -e "The 'deploy/azure/scripts/deploy.sh' script will pause and prompt you at the appropriate time to do this."
echo -e "Here are the secret ${YELLOW}names (keys)${NC} that will be created in Key Vault (these are keys from the 'secrets_to_create_in_kv' map in Terraform):"
# This list should match the keys in var.secrets_to_create_in_kv in key_vault.tf / variables.tf
declare -a secret_kv_names=(
    "POSTGRES-USER" "POSTGRES-PASSWORD" "HASURA-GRAPHQL-ADMIN-SECRET" "HASURA-GRAPHQL-JWT-SECRET"
    "STORAGE-ACCESS-KEY" "STORAGE-SECRET-KEY" "OPENAI-API-KEY" "BASIC-AUTH-FUNCTIONS-ADMIN"
    "GOOGLE-CLIENT-ID-ATOMIC-WEB" "ZOOM-CLIENT-SECRET" "TRAEFIK-USER" "TRAEFIK-PASSWORD"
    "GOOGLE-CLIENT-ID-ANDROID" "GOOGLE-CLIENT-ID-IOS" "GOOGLE-CLIENT-SECRET-ATOMIC-WEB" "GOOGLE-CLIENT-SECRET-WEB"
    "KAFKA-USERNAME" "KAFKA-PASSWORD" "OPENSEARCH-USERNAME" "OPENSEARCH-PASSWORD"
    "ZOOM-PASS-KEY" "ZOOM-CLIENT-ID" "ZOOM-SALT-FOR-PASS" "ZOOM-IV-FOR-PASS" "ZOOM-WEBHOOK-SECRET-TOKEN"
    "API-TOKEN"
    # Add suffixes for Optaplanner user/pass if they are distinct from API_TOKEN and PG user/pass
    # The current TF setup uses API-TOKEN for Optaplanner app password, and POSTGRES-* for its DB.
)
for secret_name in "${secret_kv_names[@]}"; do
    echo -e "  - ${secret_name}"
done
echo -e "\nExample command to set a secret value (run AFTER 'terraform apply' creates the Key Vault and secret placeholder):"
echo -e "${GREEN}az keyvault secret set --vault-name YOUR_KEY_VAULT_NAME --name \"SECRET-NAME-FROM-LIST\" --value \"YOUR_ACTUAL_SECRET_VALUE\"${NC}"
echo -e "For multi-line secrets or JSON, you might use '--file /path/to/secret_file.json' or ensure the value is correctly quoted."
read -p "Press Enter once you understand this secret population step..."

# 7. config.sh Management
CONFIG_AZURE_FILE="deploy/azure/config.sh"
CONFIG_AZURE_EXAMPLE_FILE="deploy/azure/config.sh.example"
print_header "Configuration File Setup (config.sh for Azure)"
if [ -f "$CONFIG_AZURE_FILE" ]; then
    echo -e "${GREEN}$CONFIG_AZURE_FILE already exists.${NC} Please ensure it is up-to-date with your Azure settings."
else
    if [ -f "$CONFIG_AZURE_EXAMPLE_FILE" ]; then
        cp "$CONFIG_AZURE_EXAMPLE_FILE" "$CONFIG_AZURE_FILE"
        echo -e "${GREEN}$CONFIG_AZURE_FILE copied from $CONFIG_AZURE_EXAMPLE_FILE.${NC}"
        echo -e "${YELLOW}You MUST review and edit $CONFIG_AZURE_FILE with your specific Azure settings.${NC}"
    else
        echo -e "${RED}Error: $CONFIG_AZURE_EXAMPLE_FILE not found. Cannot create $CONFIG_AZURE_FILE.${NC}"
        echo -e "Please restore $CONFIG_AZURE_EXAMPLE_FILE or create $CONFIG_AZURE_FILE manually."
    fi
fi

# 8. Instruct user to review and fill config.sh
echo -e "\n${YELLOW}ACTION REQUIRED:${NC} Please open ${YELLOW}$CONFIG_AZURE_FILE${NC} in a text editor."
echo -e "Review all variables and update them with your specific values for:"
echo -e "  - Azure Subscription ID, Tenant ID, Region, Resource Group Name"
echo -e "  - Project Name, Environment Name, Domain Name"
echo -e "  - AKS Cluster settings (name suffix, K8s version)"
echo -e "  - Resource naming suffixes (ACR, Key Vault, PostgreSQL)"
echo -e "  - PostgreSQL admin username and initial database name"
echo -e "  - Log Analytics Workspace ID (optional)"
echo -e "Ensure all placeholder values like 'your-...' or 'example.com' are replaced."

# 9. Completion Message
print_header "Azure Initial Configuration Guidance Complete!"
echo -e "Next steps:"
echo -e "  1. ${YELLOW}Manually create Azure Storage Account and Blob Container for Terraform backend.${NC}"
echo -e "  2. ${YELLOW}Uncomment and update the 'backend \"azurerm\"' block in 'deploy/azure/terraform/versions.tf'.${NC}"
echo -e "  3. ${YELLOW}Carefully review and update 'deploy/azure/config.sh' with all your specific Azure settings.${NC}"
echo -e "  4. After running 'terraform apply' for the first time (via main deploy script), ${YELLOW}populate the actual secret values in Azure Key Vault.${NC}"
echo -e "\nOnce these steps are done, you can proceed with running the main deployment script for Azure."
echo -e "Refer to the project's README for detailed deployment instructions."

exit 0
