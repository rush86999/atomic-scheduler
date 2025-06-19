### AWS Lambda Deployment for Quarkus Application

This document guides you through deploying your Quarkus application to AWS Lambda using the provided CloudFormation template (`template.yaml`).

#### Prerequisites

*   AWS CLI installed and configured (with necessary permissions to create the resources defined in `template.yaml`).
*   Maven or Gradle installed (matching your Quarkus project build tool).
*   An S3 bucket to upload the packaged Lambda code. **You will need to replace the placeholder S3 bucket name in the deployment commands.**

#### 1. Package Your Quarkus Application for AWS Lambda

To deploy your Quarkus application to AWS Lambda, you need to build a specific package. Ensure your project includes the `quarkus-amazon-lambda` extension. If you haven't added it yet, you can do so by running:

**Using Maven:**
```bash
./mvnw quarkus:add-extension -Dextensions="amazon-lambda"
```

**Using Gradle:**
```bash
./gradlew addExtension --extensions="amazon-lambda"
```

Then, build the package suitable for the JVM Lambda runtime:

**Using Maven:**
```bash
./mvnw clean package -Dquarkus.package.type=zip
```
(If not using the Maven wrapper, use `mvn` instead of `./mvnw`)

**Using Gradle:**
```bash
./gradlew build -Dquarkus.package.type=zip
```
(If not using the Gradle wrapper, use `gradle` instead of `./gradlew`)

This command will generate a `function.zip` file (typically in `target/` for Maven or `build/libs/` for Gradle, often named like `quarkus-app-1.0.0-SNAPSHOT-runner.zip` or `app-function.zip`). This zip file contains your application JARs and the necessary Lambda bootstrap code provided by Quarkus. Adjust the path in the S3 upload command accordingly.

#### 2. Upload Packaged Application to S3

Upload the generated `function.zip` to an S3 bucket.

```bash
# Replace 'your-s3-bucket-name' with your actual S3 bucket name
# Replace 'target/quarkus-app-1.0.0-SNAPSHOT-runner.zip' with the actual path to your generated zip file (e.g., build/libs/app-function.zip for Gradle)
aws s3 cp target/quarkus-app-1.0.0-SNAPSHOT-runner.zip s3://your-s3-bucket-name/quarkus-app.zip
```
**Note:** Ensure the S3 key here (`quarkus-app.zip`) matches the `S3KeyCode` parameter you will use during CloudFormation deployment.

#### 3. Deploy the CloudFormation Stack

Use the AWS CLI to deploy the `template.yaml` file. This template will create the Lambda function, an HTTP API Gateway, and the necessary IAM roles and permissions.

```bash
# Replace 'your-s3-bucket-name' with your S3 bucket name where you uploaded quarkus-app.zip
# Replace 'your-stack-name' with a unique name for your CloudFormation stack (e.g., quarkus-lambda-stack)
# Replace 'your-aws-region' with the AWS region you want to deploy to (e.g., us-east-1)

aws cloudformation deploy \
    --template-file template.yaml \
    --stack-name your-stack-name \
    --capabilities CAPABILITY_IAM \
    --region your-aws-region \
    --parameter-overrides \
        S3BucketCode=your-s3-bucket-name \
        S3KeyCode=quarkus-app.zip
```
*   `CAPABILITY_IAM` is required because the template creates an IAM Role for the Lambda function.
*   The `--parameter-overrides` are used to pass the S3 bucket and key for your Lambda code to the CloudFormation template.

Wait for the deployment to complete. You can check the status in the AWS CloudFormation console or using the AWS CLI:
```bash
aws cloudformation describe-stacks --stack-name your-stack-name --region your-aws-region --query "Stacks[0].StackStatus"
```

#### 4. Test Your Deployed Application

After successful deployment, the CloudFormation stack outputs will include the API Gateway endpoint URL. You can find this in the AWS CloudFormation console (select your stack, then go to the "Outputs" tab) or using the AWS CLI:

```bash
# Replace 'your-stack-name' and 'your-aws-region' accordingly
aws cloudformation describe-stacks --stack-name your-stack-name --region your-aws-region --query "Outputs[?OutputKey=='HttpApiEndpoint'].OutputValue" --output text
```

Once you have the URL, you can use a tool like `curl` or your web browser to test it. If your Quarkus app has an endpoint at `/hello`, it would be:

```bash
# Replace <api-endpoint-url> with the actual output URL
curl <api-endpoint-url>/
```
Or, if you have a specific path, for example `/hello`:
```bash
curl <api-endpoint-url>/hello
```

---

### Deploying as a Native Executable (Recommended for Performance & Cost)

For improved cold start times, lower memory usage, and potentially reduced costs on AWS Lambda, it's highly recommended to deploy your Quarkus application as a native executable.

**Benefits:**
*   **Faster Cold Starts:** Native executables start much faster than JVM-based applications, significantly reducing latency for infrequently called functions.
*   **Lower Memory Footprint:** Native applications consume less memory, which can lead to lower costs as Lambda pricing is partly based on memory allocation and duration.
*   **Optimized Performance:** Compiled directly to machine code for the target environment.

**1. Build Process for Native Lambda:**

Building a native executable for AWS Lambda typically requires a Linux environment or Docker to build against the Amazon Linux 2 environment. The `quarkus-amazon-lambda` extension facilitates this.

**Using Maven:**
Ensure your `pom.xml` is configured for native builds (often by default, the Quarkus Maven plugin handles this with the `-Dnative` profile).
```bash
# Ensure Docker is running if you're using container-based build
./mvnw clean package -Dnative -Dquarkus.native.container-build=true -Dquarkus.package.type=zip
```
*   `-Dnative`: Activates the native profile.
*   `-Dquarkus.native.container-build=true`: Instructs Quarkus to build the native executable inside a container that matches the Lambda execution environment. This is recommended for cross-platform compatibility.
*   `-Dquarkus.package.type=zip`: Ensures the output is a zip file suitable for Lambda.

**Using Gradle:**
```bash
# Ensure Docker is running if you're using container-based build
./gradlew build -Dnative -Dquarkus.native.container-build=true -Dquarkus.package.type=zip
```

This command will generate a `function.zip` file (e.g., in `target/` or `build/libs/`), but this time it will contain the native executable (often named `bootstrap`) and any necessary supporting files, instead of JARs.

**2. CloudFormation Template Adjustments (`template.yaml`):**

To deploy the native executable, you need to modify the `QuarkusLambdaFunction` resource in your `template.yaml`:

*   Change the `Runtime` property from `java11` (or `java17`) to `provided.al2`. This tells AWS Lambda to use a custom runtime based on Amazon Linux 2, which is what Quarkus native executables for Lambda target.
*   The `Handler` property (`io.quarkus.amazon.lambda.runtime.QuarkusStreamHandler::handleRequest`) usually remains the same for native Quarkus Lambda deployments, as Quarkus provides the necessary custom runtime bootstrap. If you encounter issues, double-check the Quarkus documentation for the specific version of `quarkus-amazon-lambda` you are using.

Here's a snippet of the relevant part of `template.yaml` to be modified:

```yaml
# ... inside Resources.QuarkusLambdaFunction.Properties ...
      Handler: io.quarkus.amazon.lambda.runtime.QuarkusStreamHandler::handleRequest # Or your specific handler for native, usually the same
      Runtime: provided.al2 # Changed from java11 or java17
      Code:
        S3Bucket: !Ref S3BucketCode
        S3Key: !Ref S3KeyCode
      MemorySize: 256 # Native executables often require less memory, e.g., 256MB or even 128MB
      Timeout: 30
# ...
```
**Note on MemorySize:** You can often significantly reduce `MemorySize` (e.g., to `256` or `128`) for native executables, which can further reduce costs. Test to find the optimal value for your application.

**3. Re-deployment:**

After building your native `function.zip` and modifying `template.yaml` to use the `provided.al2` runtime (and potentially adjusting `MemorySize`):
1.  Upload the new native `function.zip` to S3 (Step 2 in the main guide, ensuring the path and name match what's expected by your CloudFormation parameters).
2.  Re-run the `aws cloudformation deploy` command (Step 3 in the main guide). CloudFormation will update the Lambda function with the new runtime and code.

Testing (Step 4) and Cleaning Up (Step 6) remain the same.

---

#### Updating the Lambda Function

To update your Lambda function with new code:
1.  Repackage your Quarkus application (Step 1).
2.  Re-upload the `function.zip` to the *same S3 location* specified by `S3BucketCode` and `S3KeyCode` (Step 2).
3.  Re-run the `aws cloudformation deploy` command (Step 3). CloudFormation will detect the change in the S3 object and update the Lambda function.

#### Cleaning Up

To remove all the resources created by this stack, delete the CloudFormation stack:
```bash
# Replace 'your-stack-name' with your CloudFormation stack name
# Replace 'your-aws-region' with your AWS region
aws cloudformation delete-stack --stack-name your-stack-name --region your-aws-region
```
This will delete the Lambda function, API Gateway, IAM role, and Log Group. Remember to also delete the `quarkus-app.zip` from your S3 bucket if you no longer need it.

---
