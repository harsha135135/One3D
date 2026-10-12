# Infrastructure: the AWS demo

The One3D demo runs on demand in `ap-south-1` at `https://demo.one3d.store`, paid from AWS promotional credits, which are shared with OneCreations in the same account. Decisions and costs are in [decision 0003](../docs/decisions/0003-aws-demo-deployment.md).

```text
infra/
  iam/        the deployment user's policy (created once with the root user)
  account/    account-wide budgets (shared with OneCreations; not destroyed with the demo)
  terraform/
    bootstrap/  the S3 bucket for Terraform state (local state)
    demo/       the demo environment (state in the bootstrap bucket)
  nginx/      local, bootstrap (first certificate) and https configurations
  deploy/     deploy.sh (on the instance), remote-deploy.sh (in CI), simulator.sh
  ops/        demo.sh: start, stop, status; demo-users.sh: the synthetic sign-in users;
              restore-check.sh: restore a fresh backup into a separate database and check it
  monitoring/ Prometheus configuration, Grafana data source and the provisioned dashboard
```

## Identity

**Root** is used once, to create the IAM user `one3d-deployer` and attach the customer-managed policy [`one3d-deployer`](iam/one3d-deployer-policy.json):

```bash
aws iam create-policy --policy-name one3d-deployer --policy-document file://infra/iam/one3d-deployer-policy.json
aws iam create-user --user-name one3d-deployer
aws iam attach-user-policy --user-name one3d-deployer --policy-arn arn:aws:iam::<account>:policy/one3d-deployer
# aws login needs signin:AuthorizeOAuth2Access and signin:CreateOAuth2Token:
aws iam attach-user-policy --user-name one3d-deployer --policy-arn arn:aws:iam::aws:policy/SignInLocalDevelopmentAccess
```

The policy:
- allows only One3D-named resources (`one3d-*` buckets, repositories, roles and `/one3d/*` parameters), plus EC2, Cognito and SSM in `ap-south-1`;
- explicitly denies every OneCreations resource.

The owner sets the user's console password and MFA device in the console, without "must change password at next sign-in", because the policy does not allow changing it. The user has **no access keys**.

It is used through `aws login --profile one3d-deployer --region ap-south-1`, run in a terminal where it can wait for the browser. First sign out of any root console session, or `aws login` reuses it and stores root credentials under that profile. Check with `aws sts get-caller-identity --profile one3d-deployer`, which must show `user/one3d-deployer`. Every command below runs as this user.

**GitHub Actions** never uses this user. It assumes `one3d-demo-github-deploy` through OIDC, from the `main` branch only. The role trusts the token subject `repo:harsha135135@<owner id>/One3D@<repository id>:ref:refs/heads/main`, which is the form this repository's tokens use. It contains GitHub's numeric ids, so a different repository later created under the same name cannot match it. The prefix is the Terraform variable `github_oidc_subject_prefix`. If the role cannot be assumed, check the `sub` of a token (the error names it) against that variable.

## First-time setup

1. Create the state bucket and initialize the demo's state:
   ```bash
   terraform -chdir=infra/terraform/bootstrap init && terraform -chdir=infra/terraform/bootstrap apply
   terraform -chdir=infra/terraform/demo init -backend-config="bucket=one3d-tfstate-<account>"
   terraform -chdir=infra/terraform/demo apply
   ```
2. Put Razorpay's **test** keys into SSM. They stay out of Terraform's state, and the values are read from `.env` without being echoed:
   - `/one3d/demo/razorpay-key-id`
   - `/one3d/demo/razorpay-key-secret`
   - `/one3d/demo/razorpay-webhook-secret`
3. Set the GitHub repository variables from the Terraform outputs:
   - `AWS_DEPLOY_ROLE_ARN`, `AWS_REGION`, `DEMO_INSTANCE_ID`, `ECR_REGISTRY`, `OPS_BUCKET`
4. Add an `A` record for `demo.one3d.store` at the DNS provider (Hostinger) pointing at the `public_ip` output.
5. Merge to `main`. CI tests, pushes the images by commit SHA and deploys. On the first deploy, `deploy.sh` obtains the Let's Encrypt certificate.
6. In the Razorpay dashboard (test mode), add the webhook:
   - URL: `https://demo.one3d.store/api/payments/razorpay/webhook`
   - the same secret
   - events `payment.captured`, `order.paid`, `payment.authorized` and `payment.failed`
7. Account budgets, run once: `ALERT_EMAIL=<address> infra/account/budgets.sh`.
8. Synthetic demo users: `infra/ops/demo-users.sh customer` and `infra/ops/demo-users.sh operator`. Each creates the user in Cognito, enrols an authenticator and stores the email, password and TOTP secret as a SecureString in `/one3d/demo-users/<role>`, without printing them. To sign in by hand, read the parameter with `--with-decryption` and add its `totp_uri` to an authenticator app. Running the script again resets the user.

## Daily use

| Task | Command |
|---|---|
| Start the demo | `infra/ops/demo.sh start`. Starts the instance, redeploys (renewing the certificate if due), checks the site, then deploys the latest passing commit if it is newer. It takes about 75 seconds; for the last 15 or so the site answers 502 while the backend starts |
| Stop the demo | `infra/ops/demo.sh stop`. Backs the database up to the ops bucket, stops the containers cleanly, then stops the instance. Not in the minutes after a payment: Razorpay's webhook deliveries fail while the demo is stopped, and Razorpay disables a webhook after about a day of failures (check it in the dashboard after a long stop) |
| Check it | `infra/ops/demo.sh status` |
| Finish a simulated print | `bash /opt/one3d/app/infra/deploy/simulator.sh complete`, on the instance through SSM. The simulator's controls are reachable only on its internal network |
| Roll back | See below |

**Rolling back** to an earlier commit: Actions → "Deploy a commit" with that commit's full SHA (`gh workflow run deploy.yml --ref main -f sha=<sha>`). The workflow checks that the commit's CI run on `main` passed and that its images are in ECR, then deploys those exact images. `/opt/one3d/previous-tag` on the instance records what was running before. Each deploy also unpacks that commit's Compose and Nginx files; Nginx is recreated when its templates differ from the running ones (a hash label set by `deploy.sh`). Commits before `8b1837d` lack that label, so a deploy between two such commits leaves Nginx's configuration as it was: run `demo.sh stop` and `start` afterwards. A rollback replaces the images and those files, not the database. If a newer commit added a Flyway migration, the older code starts against the newer schema (by default Flyway ignores applied migrations newer than the code), and whether that works depends on the change. Then either deploy a fix forward or restore the backup taken before that deploy into a fresh database. Check `backend/src/main/resources/db/migration` between the two commits before rolling back.

**What survives a stop, a start and even replacing the instance:**
- the database, the simulator journal and the TLS certificates, on the separate encrypted 4 GB data volume;
- uploaded files, in the versioned S3 bucket;
- images, in ECR.

**What keeps costing money while stopped:**
- the two disks (16 GB of gp3);
- the Elastic IP, at $0.005 an hour whether the instance is running or stopped;
- stored images and files.

## Metrics and logs

Nothing here is public. Prometheus reads the backend's management port (8081) on the internal Docker network, and Grafana listens only on the instance's 127.0.0.1:3001. To open the "One3D overview" dashboard, forward that port through SSM. This needs the AWS Session Manager plugin on your machine:

```bash
aws ssm start-session --target <instance_id> --document-name AWS-StartPortForwardingSession \
  --parameters portNumber=3001,localPortNumber=3001
# then open http://localhost:3001 and sign in as admin with the password from
aws ssm get-parameter --name /one3d/demo/one3d-grafana-admin-password --with-decryption --query Parameter.Value --output text
```

What the dashboard shows:
- **API:** request rate by status, 5xx rate, and 95th-percentile latency by route.
- **Background work:** unfinished rows by kind and state, and the age of the oldest one.
- **Printing:** attempts awaiting reconciliation, start deliveries by result, simulator calls without an answer, and prints finished.
- **Backend:** scrape status, JVM heap and database connections.

Labels are fixed names only (route templates, enum values), never ids.

**Logs.** The backend writes one JSON object per line (Elastic Common Schema). Each line carries `request_id` (the same id Nginx logs as `rid=` and returns in `X-Request-Id`) and, where known, `order_id`, `job_id`, `attempt_id`, `command_id` or `artifact_id`. Nginx's access log leaves out query strings. On the instance, through SSM:

```bash
cd /opt/one3d/app && docker compose --env-file /opt/one3d/one3d.env -f compose.yaml -f compose.deploy.yaml \
  logs --no-log-prefix --since 30m backend | grep '"attempt_id":"<id>"'
```

## Backups and restoring the database

`demo.sh stop` writes a backup (`pg_dump`, custom format) to the ops bucket under `backups/` before every stop. `infra/ops/restore-check.sh` proves a backup can be restored without touching anything live:
1. It takes a fresh backup and reads it back from S3.
2. It restores it into a separate database.
3. It compares row counts and one order with everything it links to.
4. It checks that a file record still names its exact stored version.
5. It drops the copy.

To restore the **live** database from a backup, for example after a bad migration:

1. **Stop the application, so nothing dispatches:**
   ```bash
   docker compose … stop backend
   ```
   The site's API answers 502 until step 4.
2. **Restore the chosen backup over the live database** (on the instance, through SSM):
   ```bash
   aws s3 cp s3://<ops bucket>/backups/<file>.dump /tmp/restore.dump
   docker exec one3d-db-1 dropdb -U one3d one3d && docker exec one3d-db-1 createdb -U one3d one3d
   docker exec -i one3d-db-1 pg_restore -U one3d -d one3d --no-owner --exit-on-error < /tmp/restore.dump
   ```
3. **Compare the restored database with the simulator's journal before anything is dispatched.** The simulator kept its own record and may have run commands the restored database does not know about.
   - **Print attempts the restored database shows as `START_INTENT`, `RECONCILIATION_REQUIRED` or `RUNNING`:** look up each command in the journal (`GET http://simulator:8090/commands/<command_id>` from a container on the network). The worker asks the journal before it sends anything, so a command the journal already has is not started twice.
   - **A print the journal shows that the database has no record of** (it started after the backup): the printer stays busy, or its bed occupied, until an operator deals with it on the simulator. Meanwhile the simulator refuses new starts on that printer, and the backend records them as refused.
   - **Orders, uploads and payments made after the backup are not in the restored database.** Razorpay still has those payments. A later webhook for a Razorpay order the database does not know is kept for review (`UNKNOWN_PROVIDER_ORDER`), and nothing is confirmed by it.
4. **Start the application again:** run `deploy.sh` for the deployed tag, or `demo.sh start`. Then watch "Attempts awaiting reconciliation" and `/api/ops/work?status=EXHAUSTED` until both settle.

This is a single-instance demo with no replica. Anything written after the newest backup is lost when restoring.

## Replacing the instance

The instance is disposable; the data volume, the Elastic IP, the buckets, ECR and Cognito are not. To get a fresh instance (a broken root disk, a new Amazon Linux image), use the phases of [`replace-instance.sh`](../docs/evidence/2026-10-12-checkpoint-8/cloud/replace-instance.sh). It was used in Checkpoint 8, with `PLAN_FILE` set outside the repository because a saved plan holds sensitive values:

1. **`prepare`:**
   - `demo.sh stop` (a backup) and a snapshot of the data volume;
   - termination protection off;
   - a saved plan for `terraform apply -replace=aws_instance.demo`, which it refuses unless the only resources deleted are the instance and its volume attachment.
2. **`apply`:**
   - applies that plan and waits for the first boot. The boot script mounts the existing data volume and formats it only if it is empty;
   - sets the GitHub variable `DEMO_INSTANCE_ID` to the new instance, and turns protection on again.
3. **`deploy`:** deploys the current `main` commit. The certificate is reused from the data volume.

Then check the site and a few records, run `terraform plan` (it should show no changes), and delete the snapshot.

## Destroying the demo

1. Remove the GitHub repository variables (`gh variable delete …`), so a later push to `main` does not try to deploy.
2. Optionally make a final backup: `infra/ops/demo.sh stop` writes one to the ops bucket. Download it if you want to keep it, because the next step deletes the bucket.
3. Turn off termination protection:
   ```bash
   terraform -chdir=infra/terraform/demo apply -var termination_protection=false
   ```
   AWS can report the old value for about a minute after the change; check it with `aws ec2 describe-instance-attribute --attribute disableApiTermination` before step 4.
4. Destroy the demo:
   ```bash
   terraform -chdir=infra/terraform/demo destroy
   ```
   This deletes the instance, both volumes, the Elastic IP, both buckets with their files, ECR, Cognito, the parameters, the IAM roles and the GitHub OIDC provider.
5. Remove Razorpay's keys, the demo users' sign-in details (`/one3d/demo-users/*`) and anything else left under `/one3d/`:
   ```bash
   aws ssm delete-parameters --names …
   ```
6. Remove the `A` record at Hostinger, and the webhook in the Razorpay dashboard.
7. Empty the state bucket's object versions, then destroy it:
   ```bash
   terraform -chdir=infra/terraform/bootstrap destroy
   ```
8. Delete the `one3d-deployer` user and its policy (as root, or as another administrator).
9. Check that nothing remains:
   ```bash
   aws resourcegroupstaggingapi get-resources --tag-filters Key=Project,Values=one3d
   ```
   Check the next day's bill as well.

The account-wide budgets stay in place for OneCreations unless you remove them.
