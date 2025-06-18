# AWS Deployment for Atomic Application

## Overview

This document provides instructions and details for deploying the Atomic Application stack to AWS, primarily utilizing Amazon Elastic Kubernetes Service (EKS). It covers infrastructure provisioning with Terraform and Kubernetes manifest deployment with Kustomize.

This guide assumes you are deploying the "AWS" flavor of the application as orchestrated by the main `deploy.sh` script located in the parent `deploy/` directory.

## Prerequisites

1.  **Common Prerequisites:** Please ensure you have met all common prerequisites outlined in the main [deployment README](../../README.md) (e.g., Git, Docker, general CLI tools).
2.  **AWS Specific Prerequisites:**
    *   **AWS Account:** An active AWS account with appropriate spending limits and permissions.
    *   **AWS CLI:** Installed and configured. Run `aws configure` to set up your Access Key ID, Secret Access Key, default region, and default output format. Ensure the IAM user or role associated with these credentials has sufficient permissions.
        ```bash
        aws configure
        aws sts get-caller-identity # To verify your current identity
        ```
    *   **IAM Permissions:** The IAM user/role used to run Terraform will need permissions to create and manage:
        *   VPC, Subnets, Route Tables, Internet Gateway, NAT Gateways, EIPs
        *   EKS Clusters, Node Groups, OIDC Providers
        *   IAM Roles and Policies (for EKS, CodeBuild, IRSA)
        *   RDS Instances, Subnet Groups
        *   ECR Repositories
        *   AWS Secrets Manager Secrets
        *   S3 Buckets (for CodeBuild artifacts and Terraform state)
        *   DynamoDB Tables (for Terraform state locking)
        *   CloudWatch Log Groups
        *   (Potentially) ACM Certificates if used for ALB/NLB TLS termination.
        A policy close to `AdministratorAccess` might be needed for initial setup if fine-grained permissions are complex to define upfront. For production, always scope down permissions to the minimum required.

## Configuration (`deploy/aws/config.sh`)

A shell script `deploy/aws/config.sh` is used to store AWS-specific configuration variables for your deployment. The `deploy/aws/scripts/configure.sh` script helps initialize this file from `deploy/aws/config.sh.example`.

**You MUST review and update `deploy/aws/config.sh` before proceeding with deployment.**

Key variables in `config.sh.example` to configure:

*   `AWS_PROFILE`: (Optional) Your AWS CLI named profile if you use one. Default is to use the environment's default credentials.
*   `AWS_DEFAULT_REGION`: The AWS region for your deployment (e.g., `us-east-1`, `eu-west-2`).
*   `PROJECT_NAME`: A short, unique name for your project (e.g., `atomic`, `myapp`). This prefixes many resources.
*   `ENVIRONMENT_NAME`: The deployment environment (e.g., `dev`, `staging`, `prod`).
*   `DOMAIN_NAME`: Your primary domain name (e.g., `example.com`). This is used for constructing application URLs and potentially for TLS certificate management.
*   `EKS_CLUSTER_NAME`: Name for your EKS cluster (e.g., `${PROJECT_NAME}-eks-${ENVIRONMENT_NAME}`).
*   `EKS_CLUSTER_VERSION`: Desired Kubernetes version (e.g., `1.28`).
*   `RDS_DB_NAME`: Name for the initial database in your RDS instance (e.g., `atomicdb`).
*   `RDS_USERNAME`: Master username for the RDS instance (e.g., `atomicadmin`).
*   `CODEBUILD_ARTIFACTS_S3_BUCKET`: A globally unique S3 bucket name for CodeBuild artifacts. The example script attempts to create a unique name using your AWS Account ID.
*   `ACM_CERTIFICATE_ARN`: (Optional) If you have an existing ACM certificate for your `DOMAIN_NAME` that you want to use for the Traefik LoadBalancer.

### AWS Secrets Manager: Populating Secrets

The Terraform scripts (`deploy/aws/terraform/secrets_manager.tf`) will create *placeholders* for various secrets in AWS Secrets Manager. **You must manually populate these secrets with their actual values after Terraform creates them but before the Kubernetes applications that use them are fully functional.**

The `deploy/aws/scripts/deploy.sh` script will pause and prompt you at the appropriate time to do this.

**Secrets to Populate:**

The following secret name *suffixes* will be created by Terraform, prefixed with your `${PROJECT_NAME}/` (e.g., `atomic/POSTGRES_PASSWORD`):

*   `POSTGRES_USER`, `POSTGRES_PASSWORD`
*   `HASURA_GRAPHQL_ADMIN_SECRET`, `HASURA_GRAPHQL_JWT_SECRET` (JWT secret is a JSON string)
*   `TRAEFIK_USER`, `TRAEFIK_PASSWORD` (if Traefik dashboard basic auth is used)
*   `BASIC_AUTH_FUNCTIONS_ADMIN`
*   `OPENAI_API_KEY`, `API_TOKEN`
*   Google OAuth: `GOOGLE_CLIENT_ID_ANDROID`, `GOOGLE_CLIENT_ID_IOS`, `GOOGLE_CLIENT_ID_WEB`, `GOOGLE_CLIENT_ID_ATOMIC_WEB`, `GOOGLE_CLIENT_SECRET_ATOMIC_WEB`, `GOOGLE_CLIENT_SECRET_WEB`
*   Google Service: `GOOGLE_CALENDAR_ID`, `GOOGLE_CALENDAR_CREDENTIALS` (JSON string), `GOOGLE_MAP_KEY`, `GOOGLE_PLACE_API_KEY`
*   Storage: `STORAGE_ACCESS_KEY` (e.g., MinIO root user), `STORAGE_SECRET_KEY` (e.g., MinIO root password), `STORAGE_REGION`
*   Kafka: `KAFKA_USERNAME`, `KAFKA_PASSWORD` (if SASL enabled)
*   OpenSearch: `OPENSEARCH_USERNAME`, `OPENSEARCH_PASSWORD` (if security enabled)
*   Zoom: `ZOOM_CLIENT_ID`, `ZOOM_CLIENT_SECRET`, `ZOOM_PASS_KEY`, `ZOOM_SALT_FOR_PASS`, `ZOOM_IV_FOR_PASS`, `ZOOM_WEBHOOK_SECRET_TOKEN`
*   Optaplanner: `OPTAPLANNER_USERNAME`, `OPTAPLANNER_PASSWORD` (often same as `API_TOKEN`)
*   SMTP: `SMTP_HOST`, `SMTP_PORT`, `SMTP_USER`, `SMTP_PASS`, `SMTP_FROM_EMAIL`
*   Twilio: `TWILIO_ACCOUNT_SID`, `TWILIO_AUTH_TOKEN`, `TWILIO_PHONE_NO`
*   Stripe: `STRIPE_API_KEY` (secret key), `STRIPE_WEBHOOK_SECRET`
*   Other: `ONESIGNAL_APP_ID`, `ONESIGNAL_REST_API_KEY`, `SLACK_BOT_TOKEN`, `SLACK_SIGNING_SECRET`, `SLACK_CHANNEL_ID`, `JWT_SECRET`, `ENCRYPTION_KEY`, `SESSION_SECRET_KEY`

**How to Update Secrets:**

You can use the AWS Management Console or the AWS CLI.

*   **Using AWS CLI (example):**
    ```bash
    # Replace with your actual values from config.sh
    PROJECT_NAME="atomic" # Your project name
    AWS_DEFAULT_REGION="us-east-1" # Your region

    # Example for a simple string secret
    aws secretsmanager update-secret \
        --secret-id "${PROJECT_NAME}/POSTGRES_PASSWORD" \
        --secret-string "YOUR_VERY_STRONG_POSTGRES_PASSWORD" \
        --region "${AWS_DEFAULT_REGION}"

    # Example for a JSON secret (like HASURA_GRAPHQL_JWT_SECRET or GOOGLE_CALENDAR_CREDENTIALS)
    # Create a file, e.g., jwt_secret.json with content: {"type":"HS256","key":"your-long-key","claims_namespace":"xyz"}
    aws secretsmanager update-secret \
        --secret-id "${PROJECT_NAME}/HASURA_GRAPHQL_JWT_SECRET" \
        --secret-string file://path/to/your/jwt_secret.json \
        --region "${AWS_DEFAULT_REGION}"
    ```
*   **Using AWS Management Console:**
    1.  Navigate to AWS Secrets Manager in your chosen region.
    2.  Find the secret by its name (e.g., `atomic/POSTGRES_PASSWORD`).
    3.  Click "Retrieve secret value".
    4.  Click "Edit", choose "Plaintext", paste your secret value, and save. For JSON secrets, paste the JSON structure.

## Terraform S3 Backend Setup

For production and team collaboration, using an S3 backend for Terraform state is crucial. The `deploy/aws/scripts/configure.sh` script provides guidance on creating the necessary S3 bucket (for state files) and DynamoDB table (for state locking).

**Action Required:**
1.  Manually create the S3 bucket and DynamoDB table in your AWS account as guided by `configure.sh`.
2.  Uncomment and update the `backend "s3"` block in `deploy/aws/terraform/versions.tf` with your bucket name, key prefix, region, and DynamoDB table name.

## Deployment Workflow

1.  **Run Configuration Script:**
    ```bash
    cd deploy/aws/scripts
    ./configure.sh
    ```
    Follow the prompts. This will help you set up `deploy/aws/config.sh`.
2.  **Edit `config.sh`:** Manually review and update `deploy/aws/config.sh` with all your specific settings.
3.  **Set up Terraform Backend:** Update `deploy/aws/terraform/versions.tf` as described above.
4.  **Run Main Deployment Script:** Navigate to the root `deploy/` directory:
    ```bash
    cd ../..
    ./deploy.sh aws deploy
    ```
    You can add options like `--yes-terraform` to auto-approve Terraform apply, or `--yes-kubectl` to auto-approve Kustomize apply if available in `deploy.sh`.
5.  **Populate Secrets (Critical Pause):** The `deploy.sh` script (specifically the AWS `deploy.sh` sub-script) will pause after `terraform apply` and before `kubectl apply -k ...`. At this point, you **must** populate the actual secret values in AWS Secrets Manager as described above. Once done, press Enter in the script to continue.
6.  **Accessing the Application:**
    *   After successful deployment, the Traefik LoadBalancer service will be provisioned. The `deploy.sh` script might attempt to output its external DNS hostname or IP.
    *   You will need to configure DNS records for your `DOMAIN_NAME` (and any subdomains like `api.yourdomain.com`, `app.yourdomain.com` if applicable) to point to this LoadBalancer.
    *   The application should then be accessible via `https://<your-domain-name>`.
    *   The Traefik dashboard (if enabled and exposed, typically via an Ingress rule for security) might be at a path like `https://traefik.yourdomain.com/dashboard/`. The base setup enables it insecurely on port 8080; for external access, secure it via an IngressRoute.

## Key AWS Infrastructure Components

The Terraform scripts provision the following core AWS infrastructure:

*   **VPC (`vpc.tf`):** A custom Virtual Private Cloud with public, private, and database subnets across multiple Availability Zones. Includes Internet Gateway, NAT Gateways, and Route Tables.
*   **EKS Cluster (`eks_cluster.tf`):** The Kubernetes control plane, including its IAM role and OIDC provider for IRSA.
*   **EKS Node Groups (`eks_nodegroups.tf`):** Managed node groups for running Kubernetes workloads. Uses a Launch Template to associate required security groups.
*   **Security Groups (`security_groups.tf`):** Defines SGs for EKS worker nodes (allowing control plane communication, node-to-node, and application traffic) and RDS (allowing access from worker nodes).
*   **ECR (`ecr.tf`):** Elastic Container Registry repositories for custom Docker images.
*   **Secrets Manager (`secrets_manager.tf`):** Manages placeholders for application secrets.
*   **RDS (`rds.tf`):** A PostgreSQL database instance, configured to fetch its master password from Secrets Manager.
*   **IAM (`iam.tf`):** Defines IAM roles for AWS CodeBuild (CI/CD) and provides a template for IAM Roles for Service Accounts (IRSA).

## Kubernetes on AWS (EKS Specifics)

*   **AWS Load Balancer Controller:** The Traefik `Service` of type `LoadBalancer` will be provisioned by the AWS Load Balancer Controller, which should be installed in your EKS cluster (often as an add-on or manually). The overlay `deploy/kubernetes/overlays/aws/patches/traefik-service-aws-annotations.yaml` configures it as an NLB.
*   **AWS Secrets Store CSI Driver:** Kubernetes secrets are populated from AWS Secrets Manager using the Secrets Store CSI Driver and `SecretProviderClass` resources defined in `deploy/kubernetes/overlays/aws/resources/secret-provider-classes.yaml`. Pods will mount these secrets or use the synced Kubernetes `Secret` objects.
*   **ECR Image URIs:** The Kustomize overlay for AWS (`deploy/kubernetes/overlays/aws/kustomization.yaml`) updates placeholder image names in the base Kubernetes manifests to point to your ECR repository URIs.

## Troubleshooting / Common Issues for AWS

*   **IAM Permission Errors:** If `terraform apply` fails with permission errors, ensure the IAM user/role executing Terraform has the necessary permissions for all services being created. Check CloudTrail logs for detailed error messages.
*   **EKS Cluster/Node Group Failures:** These can be complex. Check the EKS console for cluster status and node group events. Common issues include insufficient instance capacity in chosen AZs, IAM role/policy misconfigurations, or networking issues (VPC/subnet/SG).
*   **Secrets Not Populating in Pods:**
    *   Verify that you manually updated the secret values in AWS Secrets Manager after Terraform created the placeholders.
    *   Check the status of the `SecretProviderClass` in the relevant namespace: `kubectl get secretproviderclass -n <namespace> <spc-name> -o yaml`.
    *   Describe the pods using the secrets and check their events: `kubectl describe pod -n <namespace> <pod-name>`.
    *   Check logs of the Secrets Store CSI Driver pods (usually in `kube-system` namespace).
    *   Ensure the EKS Node IAM Role (or the SA used by CSI driver via IRSA) has `secretsmanager:GetSecretValue` and `secretsmanager:DescribeSecret` permissions on the specific secrets.
*   **Load Balancer Issues:**
    *   Ensure the AWS Load Balancer Controller is running correctly in your cluster. Check its logs.
    *   Verify subnets tagged for `kubernetes.io/role/elb` (for public LBs) are correctly configured.
    *   Check for error messages in the service events: `kubectl describe svc traefik -n traefik-ingress`.
    *   Provisioning LBs can take several minutes.
*   **RDS Instance Creation Failures:** Check RDS console events. Common issues include incorrect DB subnet group configuration, security group rules blocking access, or invalid parameter combinations.

## Cost Considerations for AWS

Deploying this stack will incur costs on AWS. Key services that contribute to costs include:

*   **EKS Control Plane:** Hourly charge per cluster.
*   **EC2 Instances (EKS Worker Nodes):** Based on instance type and uptime.
*   **RDS Instance:** Based on instance type, storage, and uptime. Multi-AZ deployments are more expensive.
*   **NAT Gateways:** Hourly charge per NAT Gateway and data processing charges. Using one per AZ increases cost but provides HA.
*   **Elastic Load Balancers (NLB for Traefik):** Hourly charge and data processing charges.
*   **S3 Storage:** For Terraform state, CodeBuild artifacts, and potentially other uses.
*   **ECR Storage & Data Transfer:** For Docker images.
*   **AWS Secrets Manager:** Cost per secret and per API call (though often within free tier limits for moderate use).
*   **CloudWatch Logs:** For log storage.
*   **Data Transfer:** Outbound data transfer from AWS can incur significant costs.

**Recommendations:**
*   Review the pricing details for each service on the [AWS Pricing page](https://aws.amazon.com/pricing/).
*   Use the AWS Cost Explorer to monitor your spending.
*   Set up billing alerts.
*   For development/testing, consider using smaller instance types, single-AZ RDS, and a single NAT Gateway to reduce costs.
*   **Crucially, use the `deploy/aws/scripts/destroy.sh` script (or `cd deploy/aws/terraform && terraform destroy`) to remove all provisioned infrastructure when not needed to avoid ongoing charges.** Remember that `destroy.sh` also attempts to clean up Kubernetes resources like LoadBalancers first.

By understanding these components and following the deployment steps carefully, you can successfully deploy the Atomic Application stack to AWS EKS.
