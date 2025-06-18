#!/bin/bash

# Exit immediately if a command exits with a non-zero status.
set -e

# --- Configuration and Setup ---
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# PROJECT_ROOT_DIR="$(cd "${SCRIPT_DIR}/../../.." && pwd)" # Not strictly needed for this script
CONFIG_FILE="${SCRIPT_DIR}/../config.sh"

# Color codes
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m' # No Color

# Function to print error message and exit
error_exit() {
    echo -e "${RED}ERROR: $1${NC}" >&2
    exit 1
}

# Usage message
usage() {
    echo "Usage: $0 <service_name> [namespace] [kubectl_log_options...]"
    echo "Streams logs for a specified service deployed in AKS."
    echo ""
    echo "Arguments:"
    echo "  <service_name>    The name of the service (e.g., 'app', 'functions', 'postgres'). This is typically used as the 'app' or 'app.kubernetes.io/name' label value."
    echo "  [namespace]       Optional. The namespace of the service. If not provided, defaults based on service_name convention (e.g., 'app' namespace for 'app' service)."
    echo "  [kubectl_log_options...]"
    echo "                    Optional. Any additional options to pass to 'kubectl logs' (e.g., '--since=1h', '--tail=100', '-c <container_name>', '--previous')."
    echo ""
    echo "Example: $0 app app --since=10m"
    echo "         $0 postgres postgres --tail=50"
    echo "         $0 traefik traefik-ingress -c traefik --previous"
    exit 1
}

# 1. Check for required arguments
if [ -z "$1" ]; then
    usage
fi

SERVICE_NAME="$1"
NAMESPACE_ARG="$2" # Might be empty or first of kubectl_log_options
shift # Shift $1 (service_name) off

# Determine if $2 was namespace or start of kubectl options
KUBECTL_OPTIONS=() # Initialize as empty array
if [[ -n "$NAMESPACE_ARG" ]] && ! [[ "$NAMESPACE_ARG" == --* ]] && ! [[ "$NAMESPACE_ARG" == -* ]]; then
    NAMESPACE="$NAMESPACE_ARG"
    shift # Shift $NAMESPACE_ARG (namespace) off
    KUBECTL_OPTIONS=("$@") # Remaining arguments are kubectl options
else
    # If NAMESPACE_ARG is empty or looks like an option, it wasn't a namespace.
    # Prepend it back to KUBECTL_OPTIONS if it was an option.
    if [ -n "$NAMESPACE_ARG" ]; then
      KUBECTL_OPTIONS=("$NAMESPACE_ARG" "$@")
    else
      KUBECTL_OPTIONS=("$@")
    fi
    NAMESPACE="" # Will be determined later
fi


# 2. Source Configuration (for AZURE_REGION, RESOURCE_GROUP_NAME, AKS_CLUSTER_NAME_SUFFIX, etc.)
echo "Loading Azure Deployment Configuration..."
if [ -f "$CONFIG_FILE" ]; then
    # shellcheck source=../config.sh
    source "$CONFIG_FILE"
    echo -e "${GREEN}Configuration file $CONFIG_FILE loaded.${NC}"
else
    error_exit "$CONFIG_FILE not found. Please run configure.sh first or create it manually."
fi

# Validate essential variables for kubectl setup
if [ -z "$AZURE_REGION" ] || [ -z "$RESOURCE_GROUP_NAME" ] || [ -z "$PROJECT_NAME" ] || [ -z "$AKS_CLUSTER_NAME_SUFFIX" ] || [ -z "$ENVIRONMENT_NAME" ]; then
    error_exit "AZURE_REGION, RESOURCE_GROUP_NAME, PROJECT_NAME, AKS_CLUSTER_NAME_SUFFIX, and ENVIRONMENT_NAME must be set in $CONFIG_FILE."
fi

# 3. Construct expected AKS cluster name
EXPECTED_AKS_CLUSTER_NAME="${PROJECT_NAME}-aks-${AKS_CLUSTER_NAME_SUFFIX}-${ENVIRONMENT_NAME}"

# 4. Configure kubectl
echo -e "\n${GREEN}Configuring kubectl for AKS Cluster: ${EXPECTED_AKS_CLUSTER_NAME}...${NC}"
if az aks get-credentials --name "${EXPECTED_AKS_CLUSTER_NAME}" --resource-group "${RESOURCE_GROUP_NAME}" --overwrite-existing ${AZURE_SUBSCRIPTION_ID:+--subscription "$AZURE_SUBSCRIPTION_ID"}; then
    echo -e "${GREEN}kubectl configured successfully.${NC}"
else
    error_exit "Failed to configure kubectl for AKS cluster. Check Azure credentials, cluster name, RG, and AKS cluster status."
fi

# 5. Determine Namespace if not provided
if [ -z "$NAMESPACE" ]; then
    case "$SERVICE_NAME" in
        traefik)
            NAMESPACE="traefik-ingress"
            ;;
        # Add other special cases if service name doesn't match namespace convention
        # e.g., supertokens-core -> supertokens namespace
        supertokens-core)
            NAMESPACE="supertokens"
            ;;
        hasura-graphql-engine)
            NAMESPACE="hasura"
            ;;
        *)
            NAMESPACE="$SERVICE_NAME" # Default to service_name as namespace
            ;;
    esac
    echo -e "${YELLOW}Namespace not provided, defaulting to: $NAMESPACE${NC}"
fi

# 6. Find Pod(s) for the Service
echo -e "\n${GREEN}Looking for pods for service '$SERVICE_NAME' in namespace '$NAMESPACE'...${NC}"

# Define label selector based on common patterns in base manifests
LABEL_SELECTOR="app=${SERVICE_NAME}" # Primary label used in most of our services
if [ "$SERVICE_NAME" == "traefik" ]; then
    LABEL_SELECTOR="app.kubernetes.io/name=traefik,app.kubernetes.io/instance=traefik"
elif [ "$SERVICE_NAME" == "supertokens-core" ]; then # Adjust if base label is different
    LABEL_SELECTOR="app=supertokens-core"
elif [ "$SERVICE_NAME" == "hasura-graphql-engine" ]; then
    LABEL_SELECTOR="app=hasura-graphql-engine"
fi


PODS_JSON=$(kubectl get pods -n "${NAMESPACE}" -l "${LABEL_SELECTOR}" -o json)
POD_NAMES=($(echo "$PODS_JSON" | jq -r '.items[] | select(.status.phase == "Running") | .metadata.name')) # Get only running pods

if [ ${#POD_NAMES[@]} -eq 0 ]; then
    # Try alternative common label if primary fails
    LABEL_SELECTOR_ALT="app.kubernetes.io/name=${SERVICE_NAME}"
    PODS_JSON_ALT=$(kubectl get pods -n "${NAMESPACE}" -l "${LABEL_SELECTOR_ALT}" -o json)
    POD_NAMES=($(echo "$PODS_JSON_ALT" | jq -r '.items[] | select(.status.phase == "Running") | .metadata.name'))

    if [ ${#POD_NAMES[@]} -eq 0 ]; then
        echo -e "${RED}No running pods found for service '$SERVICE_NAME' with label selector '${LABEL_SELECTOR}' or '${LABEL_SELECTOR_ALT}' in namespace '$NAMESPACE'.${NC}"
        echo "Available pods in namespace '$NAMESPACE':"
        kubectl get pods -n "${NAMESPACE}" -o wide
        exit 1
    fi
fi

POD_NAME_TO_LOG="${POD_NAMES[0]}" # Default to the first running pod

if [ ${#POD_NAMES[@]} -gt 1 ]; then
    echo -e "${YELLOW}Multiple running pods found for service '$SERVICE_NAME':${NC}"
    PS3="Select a pod to stream logs from: "
    select pod_choice in "${POD_NAMES[@]}"; do
        if [[ -n "$pod_choice" ]]; then
            POD_NAME_TO_LOG="$pod_choice"
            break
        else
            echo "Invalid selection. Please try again."
        fi
    done
fi

echo -e "${GREEN}Selected pod: $POD_NAME_TO_LOG${NC}"

# 7. Stream Logs
echo -e "\n${GREEN}Streaming logs for pod '$POD_NAME_TO_LOG' in namespace '$NAMESPACE'...${NC}"
echo -e "${YELLOW}(Press Ctrl+C to stop streaming)${NC}"

# Construct kubectl logs command
LOG_CMD_ARGS=("-f" "$POD_NAME_TO_LOG" "-n" "$NAMESPACE")
LOG_CMD_ARGS+=("${KUBECTL_OPTIONS[@]}") # Add any additional user-provided kubectl options

# Check if --all-containers is already passed or if we need to check for multiple containers
has_all_containers_flag=false
has_container_flag=false
for option in "${KUBECTL_OPTIONS[@]}"; do
    if [ "$option" == "--all-containers" ]; then
        has_all_containers_flag=true
        break
    fi
    if [ "$option" == "-c" ] || [ "$option" == "--container" ]; then
        has_container_flag=true
        break
    fi
done

if ! $has_all_containers_flag && ! $has_container_flag; then
    CONTAINER_NAMES=($(kubectl get pod "$POD_NAME_TO_LOG" -n "$NAMESPACE" -o jsonpath='{.spec.containers[*].name}' 2>/dev/null || echo ""))
    if [ ${#CONTAINER_NAMES[@]} -gt 1 ]; then
        echo -e "${YELLOW}Pod '$POD_NAME_TO_LOG' has multiple containers: ${CONTAINER_NAMES[*]}.${NC}"
        echo -e "${YELLOW}Streaming logs from the first container ('${CONTAINER_NAMES[0]}') by default.${NC}"
        echo -e "${YELLOW}Use '-c <container_name>' or '--all-containers' in options for more control (e.g., $0 $SERVICE_NAME $NAMESPACE -c specific-container).${NC}"
        # LOG_CMD_ARGS+=("-c" "${CONTAINER_NAMES[0]}") # Uncomment to make this default behavior explicit
    fi
fi

kubectl logs "${LOG_CMD_ARGS[@]}"
