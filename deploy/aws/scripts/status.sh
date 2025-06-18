#!/bin/bash

# Exit immediately if a command exits with a non-zero status.
set -e

# --- Configuration and Setup ---
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT_DIR="$(cd "${SCRIPT_DIR}/../../.." && pwd)" # Assuming scripts are in deploy/aws/scripts
TERRAFORM_DIR="${PROJECT_ROOT_DIR}/deploy/aws/terraform"
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
echo "Loading AWS Deployment Configuration..."
if [ -f "$CONFIG_FILE" ]; then
    # shellcheck source=../config.sh
    source "$CONFIG_FILE"
    echo -e "${GREEN}Configuration file $CONFIG_FILE loaded.${NC}"
else
    error_exit "$CONFIG_FILE not found. Please run configure.sh first or create it manually."
fi

# Validate essential variables
if [ -z "$AWS_DEFAULT_REGION" ] || [ -z "$EKS_CLUSTER_NAME" ]; then
    error_exit "AWS_DEFAULT_REGION and EKS_CLUSTER_NAME must be set in $CONFIG_FILE."
fi

# 2. Configure kubectl
print_header "Configuring kubectl for EKS Cluster: ${EKS_CLUSTER_NAME}"
if aws eks update-kubeconfig --name "${EKS_CLUSTER_NAME}" --region "${AWS_DEFAULT_REGION}" ${AWS_PROFILE:+--profile "$AWS_PROFILE"}; then
    echo -e "${GREEN}kubectl configured successfully.${NC}"
    echo "Current kubectl context:"
    kubectl config current-context
else
    error_exit "Failed to configure kubectl for EKS cluster. Check AWS credentials and EKS cluster status."
fi

# 3. Kubernetes Resource Status
print_header "Kubernetes Node Status"
kubectl get nodes -o wide

print_header "All Pods Status"
kubectl get pods --all-namespaces -o wide

# Filter by common project namespaces if PROJECT_NAME is set and used as a prefix for namespaces
# This requires namespaces to be named like project_name-service (e.g., atomic-app, atomic-functions)
# Our base manifests create namespaces like 'app', 'functions', not prefixed by project_name directly.
# So, a more targeted approach might be to list known namespaces.
PROJECT_NAMESPACES=("default" "kube-system" "traefik-ingress" "minio" "postgres" "kafka" "opensearch" "supertokens" "hasura" "functions" "optaplanner" "handshake" "oauth" "app")

echo -e "\n${GREEN}>>> Pod Status in Project-Related Namespaces <<<${NC}\n"
for ns in "${PROJECT_NAMESPACES[@]}"; do
    echo -e "${YELLOW}--- Namespace: $ns ---${NC}"
    kubectl get pods -n "$ns" -o wide --no-headers || echo "No pods found or namespace does not exist."
    if kubectl get pods -n "$ns" -o wide --no-headers | grep -E 'Error|CrashLoopBackOff|ImagePullBackOff|Evicted|Pending'; then
        echo -e "${RED}Potential issues found in namespace $ns pods (above).${NC}"
    fi
    echo ""
done


print_header "All Services Status (includes LoadBalancers)"
kubectl get svc --all-namespaces -o wide

# If Traefik is used and Ingress objects are created (though we primarily use LoadBalancer Service for Traefik)
# print_header "Ingress Resources Status"
# kubectl get ingress --all-namespaces

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


# 4. (Optional) Terraform Output Summary
# This requires Terraform to be initialized in that directory.
# print_header "Key Terraform Outputs (from $TERRAFORM_DIR)"
# (cd "$TERRAFORM_DIR" && \
#  echo "EKS Cluster Endpoint: $(terraform output -raw eks_cluster_endpoint_output 2>/dev/null || echo 'Not available')" && \
#  echo "RDS Instance Endpoint: $(terraform output -raw rds_instance_endpoint_output 2>/dev/null || echo 'Not available')" && \
#  echo "Traefik LoadBalancer Hostname (if available from K8s service, not direct TF output): $(kubectl get svc traefik -n traefik-ingress -o jsonpath='{.status.loadBalancer.ingress[0].hostname}' 2>/dev/null || kubectl get svc traefik -n traefik-ingress -o jsonpath='{.status.loadBalancer.ingress[0].ip}' 2>/dev/null || echo 'Not available')")


print_header "Status Check Complete."
echo "Review the output above for the current state of your deployment."
