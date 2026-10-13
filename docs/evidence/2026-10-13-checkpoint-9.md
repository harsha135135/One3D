# Evidence: Checkpoint 9: V1 release preparation

```text
Checkpoint / claim:  Roadmap Checkpoint 9, the parts an assistant may do: the acceptance run
                     against one known revision, documents reconciled with the code, the
                     completion gate filled in from evidence, the five-minute demo with a
                     recording, and a conditional résumé evidence map. The owner's review,
                     signoff, release tag and résumé update are NOT done here.
Date:                2026-10-13
Revision tested:     70c941e39764bf49d60360606e4648328466d7f3 (main after PR #14). The application
                     code, Compose files, Nginx, monitoring, deploy scripts, Terraform and CI in this
                     pull request are identical to it: `git diff --name-only 70c941e HEAD` lists only
                     docs/, the top-level Markdown files, architecture.svg, infra/README.md,
                     infra/ops/replace-instance.sh and infra/iam/ (the policy file, not applied).
Environment:         AWS demo (ap-south-1, t3.small i-026fb29f6b8d6591a) as the deployment user
                     one3d-deployer; local: macOS, Docker Engine 29.6.2, Node 25.4, Playwright 1.64
Implemented by:      the assistant, under the owner's instruction of 2026-10-09 and his
                     Checkpoint 9 instructions of 2026-10-13
Simulated:           Razorpay TEST MODE (test card, test bank page); the printer is the simulator;
                     the demo users are synthetic
Owner review:        pending; this record does not sign anything off
```

## 1. The revision under test

[`revision.txt`](2026-10-13-checkpoint-9/revision.txt)
- **The deployed revision:** the instance reported `70c941e…` after `demo.sh start` deployed it ([deploy run 38400887457](https://github.com/harsha135135/One3D/actions/runs/38400887457)).
- **Its images:** in ECR as backend `sha256:046e39448dda…`, frontend `sha256:30cfb5b3362e…` and simulator `sha256:e25a973fd6c5…`.
- **Its CI run on `main`:** [38342520979](https://github.com/harsha135135/One3D/actions/runs/38342520979) passed: simulator 14 tests, backend 55 unit and 187 integration, frontend lint, build, types and 26 Playwright runs.

## 2. Acceptance results

All of these ran on 2026-10-13 against `70c941e`. "Passed" means the recorded output shows it, not that a script exited 0.

| Check | Result | Evidence |
|---|---|---|
| Clean checkout, cloned from GitHub as the README says (`docker compose up -d --build`) | **Passed.** No `.env` and nothing untracked; all services healthy. A demo order went through to handover, and an upload was VERIFIED. Prometheus scrapes the backend, Grafana answers, and 8081 is not published. After all 10 containers were recreated the records were still there; the run's volumes were then removed | [`clean-checkout.txt`](2026-10-13-checkpoint-9/clean-checkout.txt) ([script](2026-10-13-checkpoint-9/clean-checkout.sh)) |
| Edge: HTTPS, headers, ports, storage | **Passed.** TLS 1.1 refused, HTTP redirects to HTTPS, HSTS, and the CSP including `cdn.razorpay.com`. Only 80 and 443 are open. No session 401, unsigned webhook 400, no CSRF 403, 2 MB body 413. Cookies are Secure and HttpOnly. The bucket refuses anonymous access, and CORS allows only the site | [`edge-checks.txt`](2026-10-13-checkpoint-9/edge-checks.txt) |
| Monitoring private; memory | **Passed.** 8081, 3001 and 9091 are closed from outside. `/actuator/*` gives 404 through the site. Grafana is only on 127.0.0.1 and answers 401 without credentials. Prometheus target up, 16 series. Memory 890 of 1,909 MiB used | [`monitoring-checks.txt`](2026-10-13-checkpoint-9/monitoring-checks.txt) |
| Persistence across the Checkpoint 8 instance replacement | **Passed.** The simulator still knows command `1bd16179…`, the one started on the replaced instance (STARTED, COMPLETED), so its journal survived on the data volume | [`simulator-journal.txt`](2026-10-13-checkpoint-9/simulator-journal.txt) |
| Authorization (real Cognito sign-in with TOTP) | **Passed.** The customer gets 403 `ACCESS_DENIED` on 7 operator endpoints (5 reads, 2 actions) and 200 on their own orders. A request without the CSRF header gets 403, and one without a session 401 | [`access-checks.txt`](2026-10-13-checkpoint-9/access-checks.txt) |
| Catalog order | **Passed.** Order `85639488` was paid in hosted Checkout with **0 CSP violations**. Webhooks: `payment.captured` APPLIED (source WEBHOOK), `order.paid` ALREADY_RECORDED, `payment.authorized` IGNORED. Then release, print, QC, ready and handed over, and the customer's timeline | [`pay.txt`](2026-10-13-checkpoint-9/pay.txt), [`journey.txt`](2026-10-13-checkpoint-9/journey.txt) |
| Custom order, recorded with captions | **Passed on the second take** (§3). The customer uploaded the fixture STL straight to S3, and the operator quoted ₹349 (PETG). The customer accepted, which created order `a15035b4`, and paid in Checkout; all three webhook events were delivered. The operator cleared the bed and released the order. With a lost acknowledgement injected, the attempt went **START_INTENT → RECONCILIATION_REQUIRED → RUNNING** (the journal answered for the same command). Then complete, QC passed, ready, handed over, and the customer's timeline | [`../demo/one3d-v1-demo.webm`](../demo/one3d-v1-demo.webm), [`../demo/record-demo.txt`](../demo/record-demo.txt) |
| Restore | **Passed.** A fresh backup was stored in S3, read back and restored into a separate database. All 15 tables match, order `85639488`'s chain is identical, and file `3f45dde5`'s exact S3 version matches its SHA-256. The copy was dropped | [`restore-check.txt`](2026-10-13-checkpoint-9/restore-check.txt) |
| Cost | **Recorded.** AWS Budgets at 13:12 UTC: **US$0.74** usage before credits this month for the whole account (OneCreations included), **US$0.00** out of pocket | [`budgets.txt`](2026-10-13-checkpoint-9/budgets.txt) |
| Final resource state | **As before, plus data.** The instance is stopped with termination protection on, and the encrypted data volume is attached. The Elastic IP is on the instance, with no unattached volumes and no snapshots. Images and Cognito users are unchanged. The only differences are 2 more uploads and 2 more backups | [`resource-state-before.txt`](2026-10-13-checkpoint-9/resource-state-before.txt), [`resource-state-after.txt`](2026-10-13-checkpoint-9/resource-state-after.txt) ([script](2026-10-13-checkpoint-9/resource-state.sh)) |

The acceptance run itself: [`acceptance.sh`](2026-10-13-checkpoint-9/acceptance.sh).

## 3. Failures and findings during the run

- **The deployment profile resolved to the root user.** After the first `aws login`, the profile `one3d-deployer` signed in as the account's root. `aws login` had reused a signed-in root browser session, as in Checkpoint 7.
  - **What ran as root:** the identity check ran in the same command as two read-only calls, `terraform output` (which reads the state from S3) and `aws ec2 describe-instances` ("stopped"). Both changed nothing.
  - **After it was found:** all AWS work stopped until the owner signed in again and `sts get-caller-identity` showed `user/one3d-deployer`.
  - **Lesson:** check the identity on its own, before any other call. The runbook already warns about the browser session.
- **First recording take: the final click timed out.** Two causes:
  - **A UI defect (`frontend/src/app/ops/page.tsx`).** After an operator action, the queue reloads the list for the filter that was selected *when the action started*. If the filter changes while that request is in flight, the old filter's results can replace the new view. It was confirmed by reading the code and by the same view loading correctly when nothing was in flight. Nothing is lost or changed; it only displays the wrong list until the next reload. **Not fixed here,** because a code change would replace the tested revision; it is listed for the owner. The recording now waits for the action to finish.
  - **The uncertain state was not on screen.** The attempt's RECONCILIATION_REQUIRED state lasts about two seconds (one retry backoff), and the first take's caption pause covered it. The database showed the start took **2 delivery tries**, so the fault was real. The second take polls faster and shows all three states.
  - **What the failed take left behind:** order `221c0c2f`, which the operator then marked handed over through the API.
- **Static analysis** ([`dependency-scan.txt`](2026-10-13-checkpoint-9/dependency-scan.txt)):
  - **Frontend production dependencies:** `npm audit --omit=dev` finds 0 vulnerabilities.
  - **Development tooling:** 5 high findings, all one advisory ([GHSA-vfj7-8cjw-p6xm](https://github.com/advisories/GHSA-vfj7-8cjw-p6xm), `braces` stack exhaustion), reached through `eslint-config-next` → `fast-glob` → `micromatch` → `braces`.
    - It is in the linter only: not in the built image, and its inputs are the project's own glob patterns. `npm audit fix` cannot fix it without a breaking major upgrade.
    - **Disposition:** accepted for V1; revisit when `eslint-config-next` updates.
  - **Java:** dependencies are managed by Spring Boot 4.1.1's BOM. No scanner was run (OWASP dependency-check needs an NVD key).
  - **GitHub:** Dependabot alerts, secret scanning and code scanning are disabled on this repository. Enabling Dependabot alerts (free for private repositories) would cover Maven and npm; that is the owner's setting.

## 4. Documents reconciled with the code

Fixed in this checkpoint:
- **README:** the status went from "Checkpoints 2–4… later checkpoints not implemented" to "implementation ready; personal review and signoff pending". The stack is no longer "planned choices", and the websites table now has `demo.one3d.store`.
- **ARCHITECTURE.md:**
  - "planned, not implemented" → implemented, with the review pending;
  - the repository tree is now the actual one (`docs/runbooks/` and `labs/` never existed, and `infra/` has deploy, ops, iam, account and monitoring);
  - the MFA note says MFA is required for everyone in the pool;
  - the diagram's caption is updated.
- **The status lines of `v1-scope.md`, `roadmap.md` and `resume-evidence.md`:** they still said "implementation not started" or "no checkpoint has passed".
- **The process statements** in `IMPLEMENTED.md` and the roadmap said the assistant merges its own pull requests; the owner merges them.
- **`IMPLEMENTED.md`'s "Partial" rows:** schema, production, tests and setup are now complete.
- **The Checkpoint 8 record:**
  - 242 tests in CI;
  - the cost comparison waits for later billing;
  - the cleanup result is recorded;
  - the clean-checkout source is noted.
- **The deployer policy:** it named `resourcegroupstaggingapi:GetResources`, but the IAM action is `tag:GetResources`. The policy file is corrected; applying it as root is optional, because the destroy check in the runbook now uses calls the deployer already has.
- **The instance-replacement script** in `infra/ops/` now changes termination protection only through a saved plan that may change nothing else.

## 5. V1 completion gate

From [V1 scope §6](../v1-scope.md#6-v1-completion-gate). "Evidence recorded" means an assistant ran and recorded the check. It is not the owner's acceptance. Nothing in this table is ticked in the scope document.

| Gate item | Status | Evidence |
|---|---|---|
| Catalog and custom-order journeys work end to end with labelled demo data | Evidence recorded | §2 (catalog order; the custom-order recording); CP6, CP7 |
| Authentication, customer isolation and operator permissions pass the recorded tests | Evidence recorded | `SecurityIT` and the ownership tests in CI run 38342520979; §2 authorization on AWS; CP4 |
| File/quote revisions and accepted order scope remain immutable | Evidence recorded (tests) | CP5 (revision, quote and acceptance tests, in every CI run since) |
| Verified test payments advance orders once; forged/duplicate events do not release extra work | Evidence recorded | CP6 (webhook tests, negative controls); §2 real webhooks (ALREADY_RECORDED, IGNORED; forged → 400) |
| Concurrent reservations, duplicate commands, failed/uncertain attempts, restarts, bed clearance and QC pass defined scenarios | Evidence recorded | CP2, CP3 (tests and demonstrations); CP8 fault on AWS; §2 lost acknowledgement reconciled in the recording |
| **Harsha** can run the application from a clean checkout with documented prerequisites | **Pending (owner)** | The assistant's run at `70c941e` passed (§2); the item asks for Harsha's own run |
| Hosted demo, CI, Terraform exercise and HTTPS routing have recorded evidence | Evidence recorded | CP7, CP8 (instance recreated with a guarded plan; follow-up plan clean); §2 |
| Monitoring, database restore, resource cost and cleanup have recorded evidence | Evidence recorded, with one limit | CP8, §2. **Cleanup:** each exercise cleaned up and nothing was orphaned. Destroying the whole demo is documented but **not run**, because the owner chose to keep the demo |
| Code, setup, architecture, limitations and IMPLEMENTED.md agree | Reconciled by the assistant (§4); **owner confirmation pending** | §4 |
| Harsha passes the ownership checks and personally signs off on V1 completion | **Pending (owner)** | — |
| A stable V1 reference, demo and résumé evidence are ready | Demo and recording ready; résumé map drafted; **release tag pending (owner)** | [`docs/demo.md`](../demo.md), [recording](../demo/one3d-v1-demo.webm), [résumé draft](../resume-evidence.md#draft-for-harshas-review-2026-10-13) |

## 6. Runtime and cost of this window

- **The demo was up from 12:52 to 13:13 UTC (21 minutes):** about US$0.01 of EC2, against an estimate of $0.04 for a 1.5-hour window.
- **What the window added:** test orders and payments, 2 uploads and 2 backups. Nothing was deleted except the restore check's temporary database.

## Known limitations at release

- **Simulated:** one simulated printer, Razorpay test mode, one small instance. There is no high availability, and the instance's metrics are lost when it is replaced.
- **MFA for everyone:** Cognito requires it for every user, customers included.
- **The ECR token** is stored unencrypted on the instance for 12 hours. It is readable by root only, and there is no SSH.
- **Unauthenticated metrics:** the management endpoints have no authentication and are reachable only on the internal network.
- **Razorpay may disable the webhook** after about a day of failed deliveries while the demo is stopped. The order page's own check with Razorpay still confirms a payment.
- **The queue view's stale-reload defect** described in §3.
- **The Let's Encrypt certificate expires 2027-01-10.** Every start renews it once it is due.
