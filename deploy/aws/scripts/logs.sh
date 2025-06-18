#!/bin/bash

# Exit immediately if a command exits with a non-zero status.
set -e

# --- Configuration and Setup ---
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# PROJECT_ROOT_DIR="$(cd "${SCRIPT_DIR}/../../.." && pwd)" # Not strictly needed for this script if not accessing other project files
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
    echo "Usage: $0 <service_name> [namespace] [kubectl_options]"
    echo "Streams logs for a specified service."
    echo ""
    echo "Arguments:"
    echo "  <service_name>    The name of the service (e.g., 'app', 'functions', 'postgres'). This is typically used as the 'app' or 'app.kubernetes.io/name' label value."
    echo "  [namespace]       Optional. The namespace of the service. If not provided, defaults to the service_name (e.g., 'app' namespace for 'app' service) or 'default'."
    echo "  [kubectl_options] Optional. Any additional options to pass to 'kubectl logs' (e.g., '--since=1h', '--tail=100', '-c <container_name>')."
    echo ""
    echo "Example: $0 app app --since=10m"
    echo "         $0 postgres postgres"
    echo "         $0 traefik traefik-ingress -c traefik"
    exit 1
}

# 1. Check for required arguments
if [ -z "$1" ]; then
    usage
fi

SERVICE_NAME="$1"
NAMESPACE_ARG="$2" # Might be empty or kubectl options
shift # Shift $1 off

# Determine if $2 was namespace or start of kubectl options
KUBECTL_OPTIONS=("$@") # Remaining arguments are kubectl options
if [[ -n "$NAMESPACE_ARG" ]] && ! [[ "$NAMESPACE_ARG" == --* ]]; then
    NAMESPACE="$NAMESPACE_ARG"
    # Shift $NAMESPACE_ARG off if it was indeed a namespace
    # This requires KUBECTL_OPTIONS to be redefined from remaining args
    # A bit complex to perfectly parse. Simpler: if $2 doesn't start with '-', it's namespace.
    # For this script, let's assume if $2 is present and not an option, it's the namespace.
    # User can also explicitly use -n <namespace> in kubectl_options.
    # Let's refine:
    shift # Shift $NAMESPACE_ARG (or first option)
    KUBECTL_OPTIONS=("$@") # Remaining args
else
    # If NAMESPACE_ARG is empty or looks like an option, it wasn't a namespace.
    # Prepend it back to KUBECTL_OPTIONS if it was an option.
    if [[ -n "$NAMESPACE_ARG" ]] && [[ "$NAMESPACE_ARG" == --* ]]; then
      KUBECTL_OPTIONS=("$NAMESPACE_ARG" "${KUBECTL_OPTIONS[@]}")
    fi
    NAMESPACE="" # Will be determined later
fi


# 2. Source Configuration (for AWS_DEFAULT_REGION, EKS_CLUSTER_NAME, AWS_PROFILE)
echo "Loading AWS Deployment Configuration..."
if [ -f "$CONFIG_FILE" ]; then
    # shellcheck source=../config.sh
    source "$CONFIG_FILE"
    echo -e "${GREEN}Configuration file $CONFIG_FILE loaded.${NC}"
else
    error_exit "$CONFIG_FILE not found. Please run configure.sh first or create it manually."
fi

# Validate essential variables for kubectl setup
if [ -z "$AWS_DEFAULT_REGION" ] || [ -z "$EKS_CLUSTER_NAME" ]; then
    error_exit "AWS_DEFAULT_REGION and EKS_CLUSTER_NAME must be set in $CONFIG_FILE."
fi

# 3. Configure kubectl
echo -e "\n${GREEN}Configuring kubectl for EKS Cluster: ${EKS_CLUSTER_NAME}...${NC}"
if aws eks update-kubeconfig --name "${EKS_CLUSTER_NAME}" --region "${AWS_DEFAULT_REGION}" ${AWS_PROFILE:+--profile "$AWS_PROFILE"}; then
    echo -e "${GREEN}kubectl configured successfully.${NC}"
else
    error_exit "Failed to configure kubectl for EKS cluster. Check AWS credentials and EKS cluster status."
fi

# 4. Determine Namespace
if [ -z "$NAMESPACE" ]; then
    # Defaulting convention: if service name is 'app', namespace is 'app'.
    # This matches our base manifest structure.
    # For 'traefik', it's 'traefik-ingress'.
    case "$SERVICE_NAME" in
        traefik)
            NAMESPACE="traefik-ingress"
            ;;
        # Add other special cases if needed
        *)
            NAMESPACE="$SERVICE_NAME" # Default to service_name as namespace
            ;;
    esac
    echo -e "${YELLOW}Namespace not provided, defaulting to: $NAMESPACE${NC}"
fi

# 5. Find Pod(s) for the Service
echo -e "\n${GREEN}Looking for pods for service '$SERVICE_NAME' in namespace '$NAMESPACE'...${NC}"

# Common labels used in our base manifests: 'app' or 'app.kubernetes.io/name'
# Try 'app' label first, then 'app.kubernetes.io/name' if that's more standard in your setup.
# The base manifests generated use 'app: <service-name>' (e.g., app: functions, app: postgres)
# For Traefik, it's 'app.kubernetes.io/name: traefik'
LABEL_SELECTOR="app=${SERVICE_NAME}"
if [ "$SERVICE_NAME" == "traefik" ]; then
    LABEL_SELECTOR="app.kubernetes.io/name=traefik,app.kubernetes.io/instance=traefik"
fi


PODS_JSON=$(kubectl get pods -n "${NAMESPACE}" -l "${LABEL_SELECTOR}" -o json)
POD_NAMES=($(echo "$PODS_JSON" | jq -r '.items[] | select(.status.phase == "Running") | .metadata.name')) # Get only running pods

if [ ${#POD_NAMES[@]} -eq 0 ]; then
    # Try with app.kubernetes.io/name if 'app' label yielded no running pods
    LABEL_SELECTOR_ALT="app.kubernetes.io/name=${SERVICE_NAME}"
    PODS_JSON_ALT=$(kubectl get pods -n "${NAMESPACE}" -l "${LABEL_SELECTOR_ALT}" -o json)
    POD_NAMES=($(echo "$PODS_JSON_ALT" | jq -r '.items[] | select(.status.phase == "Running") | .metadata.name'))

    if [ ${#POD_NAMES[@]} -eq 0 ]; then
        echo -e "${RED}No running pods found for service '$SERVICE_NAME' with label '${LABEL_SELECTOR}' or '${LABEL_SELECTOR_ALT}' in namespace '$NAMESPACE'.${NC}"
        echo "Available pods in namespace '$NAMESPACE':"
        kubectl get pods -n "${NAMESPACE}" -o wide
        exit 1
    fi
fi

POD_NAME_TO_LOG="${POD_NAMES[0]}" # Default to the first running pod

if [ ${#POD_NAMES[@]} -gt 1 ]; then
    echo -e "${YELLOW}Multiple running pods found for service '$SERVICE_NAME':${NC}"
    select pod_choice in "${POD_NAMES[@]}" "All (not recommended for -f)"; do
        if [ "$pod_choice" == "All (not recommended for -f)" ]; then
            echo -e "${RED}Streaming logs from all pods with -f is not directly supported. Please select a single pod or run manually.${NC}"
            # Or implement logic to loop and run kubectl logs for each, but -f won't work well.
            exit 1
        elif [ -n "$pod_choice" ]; then
            POD_NAME_TO_LOG="$pod_choice"
            break
        else
            echo "Invalid selection. Please try again."
        fi
    done
fi

echo -e "${GREEN}Selected pod: $POD_NAME_TO_LOG${NC}"

# 6. Stream Logs
echo -e "\n${GREEN}Streaming logs for pod '$POD_NAME_TO_LOG' in namespace '$NAMESPACE'...${NC}"
echo -e "${YELLOW}(Press Ctrl+C to stop streaming)${NC}"

# Construct kubectl logs command
LOG_CMD_ARGS=("-f" "$POD_NAME_TO_LOG" "-n" "$NAMESPACE")
# Add any additional user-provided kubectl options
LOG_CMD_ARGS+=("${KUBECTL_OPTIONS[@]}")

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
    # Get number of containers in the pod
    CONTAINER_NAMES=($(kubectl get pod "$POD_NAME_TO_LOG" -n "$NAMESPACE" -o jsonpath='{.spec.containers[*].name}'))
    if [ ${#CONTAINER_NAMES[@]} -gt 1 ]; then
        echo -e "${YELLOW}Pod '$POD_NAME_TO_LOG' has multiple containers: ${CONTAINER_NAMES[*]}.${NC}"
        echo -e "${YELLOW}Streaming logs from the first container ('${CONTAINER_NAMES[0]}') by default.${NC}"
        echo -e "${YELLOW}Use '-c <container_name>' or '--all-containers' in options for more control.${NC}"
        # Default to first container if not specified.
        # LOG_CMD_ARGS+=("-c" "${CONTAINER_NAMES[0]}") # This can be added if truly desired as a default
    fi
fi

kubectl logs "${LOG_CMD_ARGS[@]}"
