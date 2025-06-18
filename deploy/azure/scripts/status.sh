#!/bin/bash

# Exit immediately if a command exits with a non-zero status.
set -e

# --- Configuration and Setup ---
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT_DIR="$(cd "${SCRIPT_DIR}/../../.." && pwd)" # Assuming scripts are in deploy/azure/scripts
TERRAFORM_DIR="${PROJECT_ROOT_DIR}/deploy/azure/terraform"
CONFIG_FILE="${SCRIPT_DIR}/../config.sh"

# Color codes
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m' # No Color

# Function to print a section header
print_header() {
    echo -e "\n${GREEN}>>> $1 <<<${NC}\n"
}

# Function to print error message and exit
error_exit() {
    echo -e "${RED}ERROR: $1${NC}" >&2
    exit 1
}

# 1. Source Configuration
echo "Loading Azure Deployment Configuration..."
if [ -f "$CONFIG_FILE" ]; then
    # shellcheck source=../config.sh
    source "$CONFIG_FILE"
    echo -e "${GREEN}Configuration file $CONFIG_FILE loaded.${NC}"
else
    error_exit "$CONFIG_FILE not found. Please run configure.sh first or create it manually."
fi

# Validate essential variables
if [ -z "$AZURE_REGION" ] || [ -z "$RESOURCE_GROUP_NAME" ] || [ -z "$PROJECT_NAME" ] || [ -z "$AKS_CLUSTER_NAME_SUFFIX" ] || [ -z "$ENVIRONMENT_NAME" ]; then
    error_exit "AZURE_REGION, RESOURCE_GROUP_NAME, PROJECT_NAME, AKS_CLUSTER_NAME_SUFFIX, and ENVIRONMENT_NAME must be set in $CONFIG_FILE."
fi

# 2. Construct expected AKS cluster name
# This should match the naming convention used in your Terraform scripts (aks_cluster.tf)
EXPECTED_AKS_CLUSTER_NAME="${PROJECT_NAME}-aks-${AKS_CLUSTER_NAME_SUFFIX}-${ENVIRONMENT_NAME}" # Adjust if your TF naming convention is different
# Example: local.final_aks_cluster_name from a potential aks_cluster.tf locals block

# 3. Configure kubectl
print_header "Configuring kubectl for AKS Cluster: ${EXPECTED_AKS_CLUSTER_NAME}"
echo "Resource Group: ${RESOURCE_GROUP_NAME}"
if az aks get-credentials --name "${EXPECTED_AKS_CLUSTER_NAME}" --resource-group "${RESOURCE_GROUP_NAME}" --overwrite-existing ${AZURE_SUBSCRIPTION_ID:+--subscription "$AZURE_SUBSCRIPTION_ID"}; then
    echo -e "${GREEN}kubectl configured successfully.${NC}"
    echo "Current kubectl context:"
    kubectl config current-context
else
    error_exit "Failed to configure kubectl for AKS cluster. Check Azure credentials, cluster name, RG, and AKS cluster status."
fi

# 4. Kubernetes Resource Status
print_header "Kubernetes Node Status"
kubectl get nodes -o wide

print_header "All Pods Status (Across All Namespaces)"
kubectl get pods --all-namespaces -o wide

# Define project-specific namespaces based on the K8s base manifests
PROJECT_NAMESPACES=(
    "default"
    "kube-system"
    "traefik-ingress"
    "minio"
    "postgres"
    "kafka"
    "opensearch"
    "supertokens"
    "hasura"
    "functions"
    "optaplanner"
    "handshake"
    "oauth"
    "app"
)

echo -e "\n${GREEN}>>> Pod Status in Project-Related Namespaces <<<${NC}\n"
for ns in "${PROJECT_NAMESPACES[@]}"; do
    echo -e "${YELLOW}--- Namespace: $ns ---${NC}"
    # Get pods and check for problematic statuses in one go
    pod_status_output=$(kubectl get pods -n "$ns" -o wide --no-headers 2>/dev/null || echo "No pods found or namespace does not exist.")
    echo "$pod_status_output"
    if echo "$pod_status_output" | grep -E 'Error|CrashLoopBackOff|ImagePullBackOff|Evicted|Pending|ContainerCreating|Init:'; then
        if ! echo "$pod_status_output" | grep -E 'Running|Completed'; then # If only bad statuses
             echo -e "${RED}Potential issues found in namespace $ns pods (see details above).${NC}"
        elif echo "$pod_status_output" | grep -E 'Error|CrashLoopBackOff|ImagePullBackOff|Evicted'; then # If mixed but some are definitively bad
             echo -e "${RED}Potential issues found in namespace $ns pods (see details above).${NC}"
        elif echo "$pod_status_output" | grep -E 'Pending|ContainerCreating|Init:'; then # If some are still starting
             echo -e "${YELLOW}Some pods in namespace $ns are still initializing (Pending/ContainerCreating/Init).${NC}"
        fi
    elif [[ "$pod_status_output" != "No pods found or namespace does not exist." ]]; then
        echo -e "${GREEN}All pods in $ns appear to be running or completed.${NC}"
    fi
    echo ""
done


print_header "All Services Status (includes LoadBalancers)"
kubectl get svc --all-namespaces -o wide

print_header "Persistent Volume Claims (PVCs) Status"
kubectl get pvc --all-namespaces

print_header "Storage Classes (SCs) Status"
kubectl get sc

print_header "SecretProviderClasses Status (if CSI driver is used)"
if kubectl get crd secretproviderclasses.secrets-store.csi.x-k8s.io > /dev/null 2>&1; then
    kubectl get secretproviderclasses --all-namespaces
else
    echo -e "${YELLOW}SecretProviderClass CRD not found in cluster. Skipping.${NC}"
fi


# 5. (Optional) Terraform Output Summary
# This requires Terraform to be initialized in that directory.
# print_header "Key Terraform Outputs (from $TERRAFORM_DIR)"
# (cd "$TERRAFORM_DIR" && \
#  echo "AKS Cluster Name: $(terraform output -raw aks_cluster_name_output 2>/dev/null || echo 'Not available')" && \
#  echo "PostgreSQL FQDN: $(terraform output -raw pg_flexible_server_fqdn_output 2>/dev/null || echo 'Not available')" && \
#  echo "Traefik LoadBalancer IP (if available from K8s service, not direct TF output): $(kubectl get svc traefik -n traefik-ingress -o jsonpath='{.status.loadBalancer.ingress[0].ip}' 2>/dev/null || echo 'Not available')")


print_header "Status Check Complete."
echo "Review the output above for the current state of your Azure deployment."
