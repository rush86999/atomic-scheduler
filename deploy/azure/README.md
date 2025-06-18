# Azure Deployment for Atomic Application

## Overview

This document provides instructions and details for deploying the Atomic Application stack to Microsoft Azure, primarily utilizing Azure Kubernetes Service (AKS). It covers infrastructure provisioning with Terraform and Kubernetes manifest deployment with Kustomize.

This guide assumes you are deploying the "Azure" flavor of the application as orchestrated by the main `deploy.sh` script located in the parent `deploy/` directory.

## Prerequisites

1.  **Common Prerequisites:** Please ensure you have met all common prerequisites outlined in the main [deployment README](../../README.md) (e.g., Git, Docker, general CLI tools).
2.  **Azure Specific Prerequisites:**
    *   **Azure Subscription:** An active Azure subscription with appropriate spending limits and permissions.
    *   **Azure CLI (`az`):** Installed and configured.
        *   Log in to Azure: `az login --use-device-code` (or other methods like service principal login for CI/CD).
        *   Set the correct subscription: `az account set --subscription "Your Subscription ID or Name"`
        *   Verify your context: `az account show`
    *   **Permissions:** The user or Service Principal running Terraform will need permissions to create and manage:
        *   Resource Groups (if Terraform is to create it, though often pre-existing).
        *   Virtual Networks (VNet) and Subnets.
        *   Network Security Groups (NSGs).
        *   Azure Kubernetes Service (AKS) clusters, Node Pools, and related identities.
        *   Azure Container Registry (ACR).
        *   Azure Key Vault (Key Vault, Secrets, Access Policies/RBAC).
        *   Azure Database for PostgreSQL (Flexible Server).
        *   Managed Identities (User-Assigned).
        *   Role Assignments (Azure RBAC).
        *   Private DNS Zones.
        A role like "Owner" or "Contributor" on the subscription or target resource group might be needed for initial setup if fine-grained permissions are complex. For production, always scope down permissions to the minimum required for the Service Principal.

## Configuration (`deploy/azure/config.sh`)

A shell script `deploy/azure/config.sh` is used to store Azure-specific configuration variables for your deployment. The `deploy/azure/scripts/configure.sh` script helps initialize this file from `deploy/azure/config.sh.example`.

**You MUST review and update `deploy/azure/config.sh` before proceeding with deployment.**

Key variables in `config.sh.example` to configure:

*   `AZURE_SUBSCRIPTION_ID`: Your Azure Subscription ID.
*   `AZURE_TENANT_ID`: Your Azure Tenant ID.
*   `AZURE_REGION`: The Azure region for your deployment (e.g., `EastUS`, `WestEurope`).
*   `RESOURCE_GROUP_NAME`: The Azure Resource Group where resources will be deployed. Terraform can create this if configured, or it can be a pre-existing one. The current setup assumes it might be created or specified.
*   `PROJECT_NAME`: A short, unique name for your project (e.g., `atomic`, `myapp`). This prefixes many resources.
*   `ENVIRONMENT_NAME`: The deployment environment (e.g., `dev`, `staging`, `prod`).
*   `DOMAIN_NAME`: Your primary domain name (e.g., `example.com`). Used for constructing application URLs.
*   AKS Settings:
    *   `AKS_CLUSTER_NAME_SUFFIX`: Suffix for the AKS cluster name (e.g., `cluster`).
    *   `AKS_KUBERNETES_VERSION`: Desired Kubernetes version (e.g., `1.27.9`).
*   Resource Naming Suffixes:
    *   `ACR_NAME_SUFFIX`: For Azure Container Registry (e.g., `acr` resulting in `atomicacr` if project is `atomic`).
    *   `KEY_VAULT_NAME_SUFFIX`: For Azure Key Vault (e.g., `kv`).
    *   `PG_SERVER_NAME_SUFFIX`: For PostgreSQL Flexible Server (e.g., `pgs01`).
*   PostgreSQL Settings:
    *   `PG_ADMIN_USERNAME`: Administrator username for PostgreSQL.
    *   `PG_INITIAL_DATABASE_NAME`: Name of the initial database.
*   `LOG_ANALYTICS_WORKSPACE_ID`: (Optional) Resource ID of an existing Log Analytics Workspace for AKS monitoring.

### Azure Key Vault: Populating Secrets

The Terraform scripts (`deploy/azure/terraform/key_vault.tf`) will create *placeholders* for various secrets in Azure Key Vault. **You must manually populate these secrets with their actual values after Terraform creates them but before the Kubernetes applications that use them are fully functional.**

The `deploy/azure/scripts/deploy.sh` script will pause and prompt you at the appropriate time to do this.

**Secrets to Populate:**

The following secret *names* (keys from the `secrets_to_create_in_kv` map in `key_vault.tf`) will be created by Terraform in your Azure Key Vault (e.g., `atomic-kv`):

*   `POSTGRES-USER`, `POSTGRES-PASSWORD`
*   `HASURA-GRAPHQL-ADMIN-SECRET`, `HASURA-GRAPHQL-JWT-SECRET` (JWT secret is a JSON string)
*   `STORAGE-ACCESS-KEY`, `STORAGE-SECRET-KEY` (if using for MinIO-like credentials, or Azure Storage HMAC keys)
*   `OPENAI-API-KEY`, `BASIC-AUTH-FUNCTIONS-ADMIN`, `API-TOKEN`
*   Google OAuth: `GOOGLE-CLIENT-ID-ATOMIC-WEB`, `GOOGLE-CLIENT-ID-ANDROID`, `GOOGLE-CLIENT-ID-IOS`, `GOOGLE-CLIENT-SECRET-ATOMIC-WEB`, `GOOGLE-CLIENT-SECRET-WEB`
*   Kafka: `KAFKA-USERNAME`, `KAFKA-PASSWORD`
*   OpenSearch: `OPENSEARCH-USERNAME`, `OPENSEARCH-PASSWORD`
*   Zoom: `ZOOM-CLIENT-ID`, `ZOOM-CLIENT-SECRET`, `ZOOM-PASS-KEY`, `ZOOM_SALT_FOR_PASS`, `ZOOM_IV_FOR_PASS`, `ZOOM_WEBHOOK_SECRET_TOKEN`
*   Traefik: `TRAEFIK-USER`, `TRAEFIK-PASSWORD`
    (And others as defined in `variables.tf` for `secrets_to_create_in_kv`).

**How to Update Secrets in Azure Key Vault:**

You can use the Azure Portal or Azure CLI.

*   **Using Azure CLI (example):**
    ```bash
    # Replace with your actual values (KEY_VAULT_NAME will be an output from Terraform)
    KEY_VAULT_NAME="your-actual-key-vault-name" # e.g., atomic-kv-dev or from TF output

    # Example for a simple string secret
    az keyvault secret set \
        --vault-name "${KEY_VAULT_NAME}" \
        --name "POSTGRES-PASSWORD" \
        --value "YOUR_VERY_STRONG_POSTGRES_PASSWORD"

    # Example for a JSON secret (like HASURA-GRAPHQL-JWT-SECRET)
    # Create a file, e.g., jwt_secret.json with content: {"type":"HS256","key":"your-long-key","claims_namespace":"xyz"}
    az keyvault secret set \
        --vault-name "${KEY_VAULT_NAME}" \
        --name "HASURA-GRAPHQL-JWT-SECRET" \
        --file /path/to/your/jwt_secret.json
    ```
*   **Using Azure Portal:**
    1.  Navigate to your Azure Key Vault.
    2.  Under "Objects", select "Secrets".
    3.  Click on the secret name (e.g., `POSTGRES-PASSWORD`).
    4.  Click "+ Create/Import" or select the existing placeholder version if created by Terraform and choose "+ New version".
    5.  Enter the "Secret value" and save. Ensure "Content type" is appropriate (usually plain text, or `application/json` for JSON strings).

## Terraform Azure Blob Backend Setup

For production and team collaboration, using Azure Blob Storage for Terraform state is crucial. The `deploy/azure/scripts/configure.sh` script provides guidance on creating the necessary Azure Storage Account and Blob Container.

**Action Required:**
1.  Manually create the Azure Storage Account and Blob Container in your Azure subscription as guided by `configure.sh`.
2.  Uncomment and update the `backend "azurerm"` block in `deploy/azure/terraform/versions.tf` with your storage account name, container name, resource group for the storage account, and desired state file key (path).

## Deployment Workflow

1.  **Run Configuration Script:**
    ```bash
    cd deploy/azure/scripts
    ./configure.sh
    ```
    Follow the prompts. This will help you set up `deploy/azure/config.sh`.
2.  **Edit `config.sh`:** Manually review and update `deploy/azure/config.sh` with all your specific Azure settings.
3.  **Set up Terraform Backend:** Update `deploy/azure/terraform/versions.tf` as described above.
4.  **Run Main Deployment Script:** Navigate to the root `deploy/` directory:
    ```bash
    cd ../..
    ./deploy.sh azure deploy
    ```
    You can add options like `--yes-terraform` to auto-approve Terraform apply if available in the main `deploy.sh`.
5.  **Populate Secrets (Critical Pause):** The `deploy.sh` script (specifically the Azure `deploy.sh` sub-script) will pause after `terraform apply` and before `kubectl apply -k ...`. At this point, you **must** populate the actual secret values in Azure Key Vault as described above. Once done, press Enter in the script to continue.
6.  **Accessing the Application:**
    *   After successful deployment, the Traefik LoadBalancer service will be provisioned with an external IP address. The `deploy.sh` script might attempt to output this.
    *   You will need to configure DNS records for your `DOMAIN_NAME` (and any subdomains) to point to this external IP address.
    *   The application should then be accessible via `https://<your-domain-name>`.
    *   The Traefik dashboard (if enabled and exposed securely via IngressRoute) might be at `https://traefik.yourdomain.com/dashboard/`.

## Key Azure Infrastructure Components

The Terraform scripts provision the following core Azure infrastructure:

*   **Virtual Network (VNet) (`vnet.tf`):** A custom VNet with dedicated subnets for AKS, applications, and databases.
*   **Network Security Groups (NSGs) (`nsgs.tf`):** Defines firewall rules for the subnets, controlling traffic flow.
*   **AKS Cluster (`aks_cluster.tf`):** The Kubernetes control plane and default node pool, configured with Azure CNI and necessary addons.
*   **Managed Identity (`managed_identity.tf`):** A User-Assigned Managed Identity for Kubelet, granting AKS nodes permissions to other Azure resources.
*   **ACR (`acr.tf`):** Azure Container Registry for storing custom Docker images.
*   **Key Vault (`key_vault.tf`):** Azure Key Vault for managing application secrets, with RBAC configured for AKS access.
*   **Azure Database for PostgreSQL (`postgresql.tf`):** A Flexible Server instance, VNet integrated and password managed via Key Vault.

## Kubernetes on Azure (AKS Specifics)

*   **Azure Load Balancer:** The Traefik `Service` of type `LoadBalancer` will be provisioned by Azure. The overlay `deploy/kubernetes/overlays/azure/patches/traefik-service-azure-annotations.yaml` configures it (e.g., as Standard SKU).
*   **Azure Key Vault Provider for Secrets Store CSI Driver:** Enabled as an AKS addon. Kubernetes secrets are populated from Azure Key Vault using `SecretProviderClass` resources defined in `deploy/kubernetes/overlays/azure/resources/secret-provider-classes-azure.yaml`. Placeholders (`\${KEY_VAULT_NAME}`, `\${TENANT_ID}`) in this file are substituted by the `deploy.sh` script.
*   **User-Assigned Managed Identity (UAMI) for Kubelet:** The UAMI created in `managed_identity.tf` is intended to be assigned to AKS node pools. This identity is granted "AcrPull" on ACR and "Key Vault Secrets User" on Key Vault, enabling nodes to access these resources securely.
*   **ACR Image URIs:** The Kustomize overlay for Azure (`deploy/kubernetes/overlays/azure/kustomization.yaml`) and the `deploy.sh` script update placeholder image names in the base Kubernetes manifests to point to your ACR image URIs.

## Troubleshooting / Common Issues for Azure

*   **Permissions Errors (Terraform/Azure CLI):** Ensure the Service Principal or user account running `az` commands and Terraform has "Owner" or sufficient "Contributor" rights on the subscription or target resource group, plus any specific roles needed for creating AAD objects if applicable (though not directly done by these TF scripts for app SPs).
*   **AKS Cluster/Node Pool Failures:** Check the Azure portal under "Activity log" for the AKS resource or the underlying VM Scale Sets for detailed error messages. Common issues include quota limitations for CPU/VMs in the region, invalid VM sizes, or VNet/subnet misconfigurations.
*   **Key Vault Secret Access Problems:**
    *   Verify that you manually updated the secret values in Azure Key Vault.
    *   Ensure the `\${KEY_VAULT_NAME}` and `\${TENANT_ID}` placeholders in `secret-provider-classes-azure.yaml` were correctly substituted by the `deploy.sh` script.
    *   Check the status of the `SecretProviderClass` in the relevant namespace: `kubectl get secretproviderclass -n <namespace> <spc-name> -o yaml`.
    *   Describe the pods using the secrets and check their events: `kubectl describe pod -n <namespace> <pod-name>`.
    *   Check logs of the Secrets Store CSI Driver pods (usually in `kube-system`).
    *   Verify the Managed Identity used by Kubelet (or specifically by the CSI driver if configured with Workload Identity) has the "Key Vault Secrets User" role on the Key Vault. Check IAM assignments on the Key Vault.
*   **Load Balancer Provisioning:**
    *   Ensure the Service Principal used by AKS (if not using MI for LB operations) has permissions to manage Load Balancers and Public IPs.
    *   Check service events: `kubectl describe svc traefik -n traefik-ingress`.
    *   Standard Load Balancers require Standard SKU Public IPs.
*   **PostgreSQL Flexible Server Issues:** Check deployment logs in Azure portal. Common issues involve VNet integration (subnet delegation, DNS) or firewall rules if public access was mistakenly enabled. Ensure the delegated subnet is correctly configured.

## Cost Considerations for Azure

Deploying this stack will incur costs on Azure. Key services include:

*   **AKS:** While the control plane is often free for one cluster per region in some subscription types, you pay for worker nodes, networking, and storage.
*   **Virtual Machines (AKS Worker Nodes):** Based on VM size and uptime.
*   **Azure Database for PostgreSQL (Flexible Server):** Based on SKU, storage, vCores, and uptime.
*   **Azure Load Balancer (Standard SKU):** Hourly charge and data processing.
*   **Azure Container Registry (ACR):** Based on SKU (Basic, Standard, Premium) and storage used.
*   **Azure Key Vault:** Based on SKU and operations (though often within free tier limits for moderate use).
*   **Azure Monitor (Log Analytics):** For log and metric storage and ingestion.
*   **Storage (Azure Blob, Managed Disks for PVCs):** Based on type and amount of storage.
*   **Bandwidth:** Outbound data transfer.

**Recommendations:**
*   Review pricing details on the [Azure Pricing page](https://azure.microsoft.com/pricing/).
*   Use the Azure Cost Management + Billing tools to monitor spending.
*   Set up budgets and alerts.
*   For development/testing, use smaller VM sizes, lower-tier ACR/Key Vault/PostgreSQL SKUs, and consider stopping or deleting resources when not in use.
*   **Crucially, use the `deploy/azure/scripts/destroy.sh` script (or `cd deploy/azure/terraform && terraform destroy`) to remove all provisioned infrastructure when not needed to avoid ongoing charges.** Remember that `destroy.sh` also attempts to clean up Kubernetes resources.

By understanding these components and following the deployment steps carefully, you can successfully deploy the Atomic Application stack to Azure AKS.
