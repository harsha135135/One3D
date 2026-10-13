# The five-minute V1 demo

How to show One3D V1 in about five minutes, live on `https://demo.one3d.store` or from the recording. It follows the [roadmap's outline](roadmap.md#five-minute-v1-demo). Every claim below links to its evidence. The printer is a **simulator** and payments are **Razorpay test mode**: say so first.

## Before the demo (about 5 minutes)

1. **Start the demo:** `infra/ops/demo.sh start`, as the deployment user. It takes about 75 seconds and deploys the latest tested `main` commit.
2. **Sign-in details:** the demo users are `customer.demo@example.com` and `operator.demo@example.com`. Each needs a password and a TOTP code:
   ```bash
   aws ssm get-parameter --name /one3d/demo-users/customer --with-decryption --query Parameter.Value --output text
   aws ssm get-parameter --name /one3d/demo-users/operator --with-decryption --query Parameter.Value --output text
   ```
   Add each `totp_uri` to an authenticator app once. Use two browser profiles, or a private window, so both users can be signed in at the same time.
3. **The dashboard (optional):** forward Grafana through SSM (`infra/README.md`, "Metrics and logs") and sign in.
4. **Have the fixture ready:** [`demo/fixtures/wall-hook.stl`](demo/fixtures/wall-hook.stl), a synthetic two-box model (1.3 KB, 24 triangles).
5. **The lost-acknowledgement fault:** turn it on just before the operator starts the print. It runs on the instance through SSM, because the simulator's controls are not public. This is the form the recording used; its inner command was also checked against the local stack:
   ```bash
   INSTANCE=$(terraform -chdir=infra/terraform/demo output -raw instance_id)
   CMD='docker run --rm --network one3d_default curlimages/curl:8.17.0 -s -X POST -H "Content-Type: application/json" -d "{\"dropNextStartAck\": true}" http://simulator:8090/dev/printers/0d3d0000-0000-4000-8000-000000000101/faults'
   aws ssm send-command --instance-ids "$INSTANCE" --document-name AWS-RunShellScript \
     --parameters "$(jq -n --arg c "$CMD" '{commands: [$c]}')"
   ```

## The script

| Time | Show | Say | Proven in |
|---|---|---|---|
| 0:00–0:45 | The catalog page; the architecture diagram | One Spring Boot backend with PostgreSQL, a Next.js frontend, a separate printer simulator with its own journal, private S3, Cognito, Razorpay test mode. One small EC2 instance behind Nginx. **No real printer, no real money.** | [ARCHITECTURE.md](../ARCHITECTURE.md), [CP7](evidence/2026-10-12-checkpoint-7.md) |
| 0:45–1:30 | **Customer:** Custom part → upload the fixture → Send request. **Operator:** Requests → the request → Download → Send quote. **Customer:** accept the quote | The browser uploads straight to S3 with a presigned PUT that fixes the size and type. The backend pins the exact stored version and checks its SHA-256. The quote is tied to that revision and expires, and accepting creates exactly one order. | [CP5](evidence/2026-10-10-checkpoint-5.md), [CP7 S3 checks](evidence/2026-10-12-checkpoint-7.md#3-rules-enforcement-and-proof-on-aws) |
| 1:30–2:15 | **Customer:** Pay with Razorpay (test mode), with the test card and "Success" on the test bank page. **Operator:** queue → Release | Only a verified, captured payment confirms the order: a signed webhook, or the backend asking Razorpay. The browser's report never counts. Duplicate events are recorded once. Payment does not start production; release is a separate operator action. | [CP6](evidence/2026-10-12-checkpoint-6.md), [CP7 addendum: webhook delivery](evidence/2026-10-12-checkpoint-7.md) |
| 2:15–3:15 | **Operator:** Printers → mark bed clear. Turn on the fault. Job → Start attempt; reload | The start is recorded first (START_INTENT), then a worker sends it. The reply is lost, so the attempt is RECONCILIATION_REQUIRED and keeps the printer: a timeout is not permission to start again. The worker asks the simulator's journal about the same command id. It had started, so it becomes RUNNING and nothing runs twice. | [CP3](evidence/2026-10-09-checkpoint-3.md), [CP8 fault on AWS](evidence/2026-10-12-checkpoint-8.md#8-fault-exercise-on-aws-found-through-metrics-and-logs) |
| 3:15–4:00 | Finish the print (`simulator.sh complete` through SSM). Job → QC Passed. Queue → Ready for handoff → Handed over. **Customer:** the order's progress | Completion is not acceptance: whole-part QC decides. The bed stays occupied until someone confirms the part was removed, and that clearance belongs to that print. The customer's timeline is built from recorded facts. | [CP2](evidence/2026-10-09-checkpoint-2.md), [CP3](evidence/2026-10-09-checkpoint-3.md), [CP6](evidence/2026-10-12-checkpoint-6.md) |
| 4:00–5:00 | Grafana (the 'unknown' delivery and its recovery); the GitHub Actions runs; the evidence folder | 242 backend tests, 187 of them integration tests against real PostgreSQL. CI blocks promotion when a test fails. Images are pushed by commit and deployed through SSM, and a rollback is checked from outside. The instance was recreated with Terraform without losing data. A restore was verified. **Limits:** one simulated printer, test payments, one small instance, MFA for every user. | [CP7](evidence/2026-10-12-checkpoint-7.md), [CP8](evidence/2026-10-12-checkpoint-8.md), [CP9](evidence/2026-10-13-checkpoint-9.md) |

## If the live demo is not available

- **The recording:** [`demo/one3d-v1-demo.webm`](demo/one3d-v1-demo.webm), with its step log [`demo/record-demo.txt`](demo/record-demo.txt). It is the same journey on the deployed site, with captions. It was made by [`demo/record-demo.cjs`](demo/record-demo.cjs), which drives the real UI.
- **Locally:** `docker compose up -d --build`, then `http://localhost:8088`. Sign in on the mock provider's form with any name (names starting with `operator` are operators). The demo payment shortcut replaces Razorpay. Inject the same fault with `curl -X POST -H 'Content-Type: application/json' -d '{"dropNextStartAck": true}' http://127.0.0.1:8090/dev/printers/0d3d0000-0000-4000-8000-000000000101/faults`.
- **Screenshots:** in each checkpoint's evidence folder.

## Afterwards

Run `infra/ops/demo.sh stop`, but not in the minutes after a payment: Razorpay's webhook deliveries fail while the demo is stopped. The stop writes a database backup first.
