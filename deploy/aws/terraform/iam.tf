# --- Data Sources ---
data "aws_caller_identity" "current" {}

data "aws_region" "current" {}

# --- Variables ---
variable "project_name" {
  description = "Name of the project, used for tagging and naming resources."
  type        = string
}

variable "aws_account_id" {
  description = "AWS Account ID where the resources are deployed. Defaults to current caller identity."
  type        = string
  default     = "" # Will be populated by data.aws_caller_identity.current.account_id if empty
}

variable "eks_oidc_provider_arn" {
  description = "ARN of the EKS OIDC Identity Provider (output from eks_cluster.tf)."
  type        = string
  # Example: arn:aws:iam::123456789012:oidc-provider/oidc.eks.us-east-1.amazonaws.com/id/EXAMPLED539D4633E53BF441C14A35A56B5
}

variable "eks_oidc_provider_url_no_prefix" {
  description = "URL of the EKS OIDC Identity Provider without the 'https://' prefix (e.g., oidc.eks.region.amazonaws.com/id/EXAMPLE). Output from eks_cluster.tf."
  type        = string
  # Example: oidc.eks.us-east-1.amazonaws.com/id/EXAMPLED539D4633E53BF441C14A35A56B5
}

variable "codebuild_artifacts_s3_bucket_name" {
  description = "Name of the S3 bucket used for CodeBuild artifacts."
  type        = string
  # Example: "myproject-codebuild-artifacts" - this bucket should be created separately or in another module.
}

# --- IAM Role for AWS CodeBuild ---
resource "aws_iam_role" "codebuild_role" {
  name = "${var.project_name}-codebuild-role"

  assume_role_policy = jsonencode({
    Version   = "2012-10-17",
    Statement = [
      {
        Action    = "sts:AssumeRole",
        Effect    = "Allow",
        Principal = {
          Service = "codebuild.amazonaws.com"
        }
      }
    ]
  })

  tags = {
    Name      = "${var.project_name}-codebuild-role"
    Project   = var.project_name
    Terraform = "true"
  }
}

resource "aws_iam_policy" "codebuild_policy" {
  name        = "${var.project_name}-codebuild-policy"
  description = "Policy for AWS CodeBuild to access ECR, S3, CloudWatch Logs, Secrets Manager, and EKS."

  # Use a HEREDOC for the policy JSON for readability
  policy = jsonencode({
    Version   = "2012-10-17",
    Statement = [
      {
        Effect   = "Allow",
        Action   = [
          "ecr:GetAuthorizationToken"
        ],
        Resource = ["*"] # GetAuthorizationToken requires "*"
      },
      {
        Effect   = "Allow",
        Action   = [
          "ecr:BatchCheckLayerAvailability",
          "ecr:InitiateLayerUpload",
          "ecr:UploadLayerPart",
          "ecr:CompleteLayerUpload",
          "ecr:PutImage"
        ],
        # Scope down to repositories for this project if possible, e.g., by convention ${var.project_name}-*
        Resource = ["arn:aws:ecr:${data.aws_region.current.name}:${coalesce(var.aws_account_id, data.aws_caller_identity.current.account_id)}:repository/${var.project_name}-*"]
      },
      {
        Effect   = "Allow",
        Action   = [
          "logs:CreateLogGroup",
          "logs:CreateLogStream",
          "logs:PutLogEvents"
        ],
        # Scope down to CodeBuild log groups
        Resource = ["arn:aws:logs:${data.aws_region.current.name}:${coalesce(var.aws_account_id, data.aws_caller_identity.current.account_id)}:log-group:/aws/codebuild/${var.project_name}-*:*"]
      },
      {
        Effect   = "Allow",
        Action   = [
          "s3:GetObject",
          "s3:PutObject",
          "s3:ListBucket" // ListBucket is on the bucket, GetObject/PutObject on objects within
        ],
        Resource = [
          "arn:aws:s3:::${var.codebuild_artifacts_s3_bucket_name}",
          "arn:aws:s3:::${var.codebuild_artifacts_s3_bucket_name}/*"
        ]
      },
      {
        Effect   = "Allow",
        Action   = [
          "secretsmanager:GetSecretValue",
          "secretsmanager:DescribeSecret"
        ],
        # Scope down to secrets used by this project, e.g., by naming convention `${var.project_name}/*`
        Resource = ["arn:aws:secretsmanager:${data.aws_region.current.name}:${coalesce(var.aws_account_id, data.aws_caller_identity.current.account_id)}:secret:${var.project_name}/*"]
      },
      {
        Effect   = "Allow",
        Action   = [
          "eks:DescribeCluster"
          # Add other EKS actions if CodeBuild needs to interact with the cluster beyond Describe,
          # e.g., "eks:ListClusters", or permissions to update kubeconfig for kubectl commands.
          # For kubectl, consider using a dedicated K8s Service Account with IRSA if CodeBuild deploys.
        ],
        Resource = ["arn:aws:eks:${data.aws_region.current.name}:${coalesce(var.aws_account_id, data.aws_caller_identity.current.account_id)}:cluster/${var.project_name}-*"] # Assuming EKS cluster name follows this pattern
      },
      # Allow CodeBuild to access KMS keys if secrets or S3 artifacts are encrypted with CMKs it needs to use.
      # {
      #   Effect = "Allow",
      #   Action = [
      #     "kms:Decrypt",
      #     "kms:Encrypt",
      #     "kms:GenerateDataKey"
      #   ],
      #   Resource = ["arn:aws:kms:REGION:ACCOUNT_ID:key/YOUR_KMS_KEY_ID"] # Specify KMS key ARN(s)
      # }
    ]
  })

  tags = {
    Name      = "${var.project_name}-codebuild-policy"
    Project   = var.project_name
    Terraform = "true"
  }
}

resource "aws_iam_role_policy_attachment" "codebuild_policy_attachment" {
  role       = aws_iam_role.codebuild_role.name
  policy_arn = aws_iam_policy.codebuild_policy.arn
}


# --- Example: IAM Role for Service Account (IRSA) for a Kubernetes Service Account ---
# This section is commented out and serves as a template.
# Replace placeholders like <KSA_NAME>, <NAMESPACE>, <POLICY_NAME>, <BUCKET_NAME> etc.

/*
variable "example_irsa_s3_bucket_name" {
  description = "Example S3 bucket name for IRSA demo policy."
  type        = string
  default     = "my-app-specific-bucket" # This bucket should exist or be created by another module
}

resource "aws_iam_role" "example_irsa_role" {
  name = "${var.project_name}-example-app-irsa-role"

  assume_role_policy = jsonencode({
    Version   = "2012-10-17",
    Statement = [
      {
        Effect    = "Allow",
        Principal = {
          Federated = var.eks_oidc_provider_arn # ARN of the EKS OIDC provider
        },
        Action    = "sts:AssumeRoleWithWebIdentity",
        Condition = {
          StringEquals = {
            # Ensure this matches the OIDC provider URL (without "https://") and the KSA details
            "${var.eks_oidc_provider_url_no_prefix}:sub" = "system:serviceaccount:<NAMESPACE>:<KSA_NAME>",
            # Optional: Add audience check for extra security
            # "${var.eks_oidc_provider_url_no_prefix}:aud" = "sts.amazonaws.com" # Default audience for EKS OIDC
          }
        }
      }
    ]
  })

  tags = {
    Name      = "${var.project_name}-example-app-irsa-role"
    Project   = var.project_name
    Terraform = "true"
    IRSAExample = "true"
  }
}

resource "aws_iam_policy" "example_irsa_policy" {
  name        = "${var.project_name}-example-app-irsa-policy"
  description = "Example policy for an IRSA role, e.g., granting S3 access."

  policy = jsonencode({
    Version   = "2012-10-17",
    Statement = [
      {
        Effect   = "Allow",
        Action   = [
          "s3:GetObject",
          "s3:PutObject",
          "s3:DeleteObject",
          "s3:ListBucket"
        ],
        Resource = [
          "arn:aws:s3:::${var.example_irsa_s3_bucket_name}",
          "arn:aws:s3:::${var.example_irsa_s3_bucket_name}/*"
        ]
      }
      # Add other permissions as needed by the service account
    ]
  })

  tags = {
    Name      = "${var.project_name}-example-app-irsa-policy"
    Project   = var.project_name
    Terraform = "true"
  }
}

resource "aws_iam_role_policy_attachment" "example_irsa_policy_attachment" {
  role       = aws_iam_role.example_irsa_role.name
  policy_arn = aws_iam_policy.example_irsa_policy.arn
}

# Output for the example IRSA role ARN
output "example_irsa_role_arn" {
  description = "ARN of the example IAM Role for Service Account (IRSA)."
  value       = aws_iam_role.example_irsa_role.arn
}

*/

# --- Outputs (typically in outputs.tf) ---
# Moved to outputs.tf as per best practice.
# output "codebuild_role_arn" {
#   description = "ARN of the IAM Role for AWS CodeBuild."
#   value       = aws_iam_role.codebuild_role.arn
# }
