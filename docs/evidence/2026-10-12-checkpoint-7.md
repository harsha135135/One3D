# Evidence: Checkpoint 7: Docker, AWS, Nginx and one CI pipeline

```text
Checkpoint / claim:  Roadmap Checkpoint 7. The application runs from a clean checkout with the
                     documented Compose steps and keeps its records when containers are
                     replaced. One small, on-demand AWS demo (ap-south-1) serves it at
                     https://demo.one3d.store: only Nginx is reachable, on 80 and 443, with
                     HTTPS, real Cognito sign-in with required TOTP, private versioned S3
                     with presigned uploads and Razorpay test mode. One GitHub Actions
                     pipeline tests every change and deploys only immutable images of main
                     commits whose tests passed; a failing test blocks promotion. The demo
                     stops and starts without losing its database or files.
Date:                2026-10-12
Code revision:       Part 1: 23a9df0, 5abea65, f05c92e, f5a2447 (PR #8, merged as 70ef808).
                     Part 2 on feat/checkpoint-7-part-2: b2d4044 (OIDC subject), 33562fc,
                     3cf5302, cc2bd77, 1b6a4e1 (CSP), and this record.
Deployed revision:   70ef808 (deployed by CI run 38229346695 on 2026-10-12 09:34 UTC). Every
                     cloud check below ran against it. Part 2's CSP change is not deployed yet.
Environment:         Local: macOS (arm64), Docker Engine 29.6.2, Compose 5.3.1; check scripts
                     with Node 25.4 and Playwright 1.64 (Chromium).
                     AWS: ap-south-1; EC2 t3.small (Amazon Linux 2023, x86), Docker 25.0.16,
                     Compose v5.6.0; Terraform 1.16.5 with AWS provider 6.x; Cognito (Lite);
                     S3; ECR; SSM. GitHub Actions on ubuntu-24.04. Let's Encrypt (YE1).
Implemented by:      the assistant, under the owner's instruction of 2026-10-09
Simulated:           Razorpay is in test mode (test card, Razorpay's demo bank page; no real
                     money). The printer is the simulator; a print is finished through SSM.
                     The demo users are synthetic (customer.demo@ and operator.demo@example.com).
Owner review:        deferred by the owner until V1 is complete
```

Design: [decision 0003](decisions/0003-aws-demo-deployment.md) (the owner approved the setup, costs and identity plan on 2026-10-12). Runbook: [`infra/README.md`](../infra/README.md).

## 1. Automated checks

| Suite | Where | Result |
|---|---|---|
| backend | CI run [38229346695](https://github.com/harsha135135/One3D/actions/runs/38229346695) on `70ef808` (push to `main`) | **232 passed**: 51 unit (with the new `DeploymentGuardTest`, 5 tests), 181 integration |
| simulator | same run | 14 passed |
| frontend | same run | Lint, build, types; 26 Playwright runs passed |
| images | same run | All three images built and pushed to ECR as `70ef808…` (6 min 43 s) |
| deploy | same run | Deployed `70ef808` through SSM (1 min 10 s); "previous: none" |

The CI result for this part's final commit is linked from its pull request.

## 2. Roadmap demonstrations

| Demonstration | Result | Evidence |
|---|---|---|
| Start from a clean checkout using documented steps | A fresh clone (no `.env`, nothing untracked) ran `docker compose up -d --build` as the README says. It was cloned from the local repository at `3cf5302`, not from GitHub as the README's command does; the content is the same commit. All services were healthy and the site was on `http://localhost:8088`. Then: a customer order, the demo shortcut, an operator release, a print on the simulator, QC, ready and handed over; a file uploaded through the local `/s3` route and VERIFIED | [`local/clean-checkout.txt`](2026-10-12-checkpoint-7/local/clean-checkout.txt) ([script](2026-10-12-checkpoint-7/local/clean-checkout.sh)) |
| Replace containers without losing expected records | **Locally:** all 8 containers recreated; the order (HANDED_OVER, 6 timeline facts) and the file (VERIFIED) were still there. **On AWS:** all 5 containers recreated from their images; database rows the same before and after (orders=1, artifacts=1, facts=1); the order and file read again through the site after signing in anew | [`local/clean-checkout.txt`](2026-10-12-checkpoint-7/local/clean-checkout.txt) §6; [`cloud/replace-containers.txt`](2026-10-12-checkpoint-7/cloud/replace-containers.txt), [`cloud/recheck-after-recreate.txt`](2026-10-12-checkpoint-7/cloud/recheck-after-recreate.txt) |
| Block promotion when a required test fails | Draft PR #9 changed one expected value. Backend tests failed, the Images job was **skipped**, and Deploy was skipped. "Deploy a commit" on `main` with that SHA failed at "The commit's CI run on main passed" before requesting AWS credentials. Started from the PR branch, the deploy job was skipped. PR #9 was closed unmerged | [`local/promotion-blocked.txt`](2026-10-12-checkpoint-7/local/promotion-blocked.txt) |
| Deploy a known image revision and document how to revert it | CI deployed the images tagged `70ef808…` (ECR tags are immutable); the instance records `/opt/one3d/deployed-tag` and `previous-tag`. The revert procedure, including what a rollback across a database migration means, is in the runbook. **A revert has not been run yet**: only one revision exists in ECR until this part merges | [`cloud/stop-start.txt`](2026-10-12-checkpoint-7/cloud/stop-start.txt) ("Deployed 70ef808…"), [`infra/README.md`](../infra/README.md#daily-use) |
| Verify HTTPS routing; database, backend and operational ports not public | From outside: only 80 and 443 accept connections (22, 3000, 5432, 8080, 8090 and 9090 do not). HTTP redirects to HTTPS. TLS 1.1 is refused. HSTS and the other security headers are present. `/api` routes to the backend, which answers 401 to anonymous requests | [`cloud/edge-checks.txt`](2026-10-12-checkpoint-7/cloud/edge-checks.txt) ([script](2026-10-12-checkpoint-7/cloud/edge-checks.sh)) |

## 3. Rules, enforcement and proof on AWS

| Rule | Enforced by | Proven by |
|---|---|---|
| Only Nginx is reachable, on 80 and 443; no SSH | Security group (`network.tf`); `compose.deploy.yaml` publishes only Nginx | `edge-checks.txt` §3 |
| HTTPS only, TLS 1.2+, HSTS | `infra/nginx/https` template; Let's Encrypt | `edge-checks.txt` §1–2 |
| Small request bodies on `/api` (the webhook is anonymous and read before its signature check) | `client_max_body_size 1m` | `edge-checks.txt` §4: a 2 MB body gets 413 |
| The `aws` profile cannot start with mock or local settings | `DeploymentGuard` | `DeploymentGuardTest` (5); [`local/aws-profile-rehearsal.txt`](2026-10-12-checkpoint-7/local/aws-profile-rehearsal.txt) |
| Session cookies are Secure, HttpOnly and SameSite=Lax | `application-aws.yaml` | `edge-checks.txt` §5 |
| Sign-in is Cognito with TOTP for everyone; users are created only by an administrator | `identity.tf` (MFA required, admin-create-only, `operators` group) | Every signed-in check below signed in through the hosted pages with password and TOTP; `/api/me` showed CUSTOMER, or CUSTOMER and OPERATOR |
| Files are private; S3 enforces the signed size, type and signature | Bucket public-access block, TLS-only policy; presigned PUT with signed `Content-Length` and type | `edge-checks.txt` §6 (anonymous 403; CORS only for the site). [`cloud/files.txt`](2026-10-12-checkpoint-7/cloud/files.txt): one byte more, a different type and a changed signature all get 403 `SignatureDoesNotMatch`; the exact PUT gets 200; the check on the instance read the pinned version (VERIFIED, SHA-256 matches); the download is an attachment of the same bytes |
| Only images of `main` commits whose tests passed are deployed | `images` needs all test jobs; ECR tags immutable; `deploy.yml` checks the commit's CI run on `main`; the deploy role trusts only `main` | `promotion-blocked.txt` |
| The deployment user cannot touch OneCreations and works only in `ap-south-1` | Explicit denies in [`one3d-deployer-policy.json`](../infra/iam/one3d-deployer-policy.json) | [`cloud/identity-boundaries.txt`](2026-10-12-checkpoint-7/cloud/identity-boundaries.txt): OneCreations' Amplify app, media bucket and budget are refused; EC2 in `us-east-1` is refused; One3D resources are allowed |
| The database and files survive container replacement and stop/start | Separate encrypted data volume, mounted by id before Docker starts; versioned S3 | §2 above; §5 below. Instance replacement is designed for but was not exercised here; it is a Checkpoint 8 exercise |
| The simulator's controls are not public | Not published; reachable only on the internal Docker network | `edge-checks.txt` §3 (8090 closed); `journey.txt` (finished through SSM) |

## 4. Payments and the full journey on AWS

All runs used the synthetic demo users and Razorpay test mode. Output: [`cloud/pay.txt`](2026-10-12-checkpoint-7/cloud/pay.txt), [`cloud/journey.txt`](2026-10-12-checkpoint-7/cloud/journey.txt), [`cloud/csp.txt`](2026-10-12-checkpoint-7/cloud/csp.txt), [`cloud/pay-candidate-csp.txt`](2026-10-12-checkpoint-7/cloud/pay-candidate-csp.txt); script [`cloud/cloud-checks.cjs`](2026-10-12-checkpoint-7/cloud/cloud-checks.cjs).

- **Payment in hosted Checkout on the deployed site:**
  - The customer placed order `9b1cb5a7` (₹499) and paid with Razorpay's test card, choosing Success on Razorpay's demo bank page.
  - The order page reported "Payment received", and the order is CONFIRMED by `RAZORPAY_TEST`.
  - The database shows one payment: `RECONCILIATION | APPLIED | 49900 INR`. The backend's own check with Razorpay recorded it; the browser's report was not used.
- **Full journey (operator, through the site):**
  - Released, and the background worker delivered the start to the simulator (RUNNING).
  - `simulator.sh complete` ran on the instance through SSM.
  - AWAITING_QC, QC passed, ready for handoff, handed over.
  - The customer's timeline: PLACED, CONFIRMED (Paid with Razorpay test mode), RELEASED_TO_PRODUCTION, QC_PASSED, READY_FOR_HANDOFF, HANDED_OVER.
  - Screenshots: [customer](2026-10-12-checkpoint-7/cloud/journey-customer-order.png), [operator job](2026-10-12-checkpoint-7/cloud/journey-operator-job.png), [Checkout](2026-10-12-checkpoint-7/cloud/pay-1-checkout.png).
- **Content-Security-Policy:**
  - Every customer and operator page loaded with 0 violations under the deployed policy (`csp.txt`).
  - During Checkout there was one violation: Checkout.js loads Razorpay's risk-detection script from `cdn.razorpay.com`, which `script-src` did not allow. The payment still succeeded.
  - Part 2 adds `https://cdn.razorpay.com` to `script-src` (`1b6a4e1`). Razorpay's Checkout script from `checkout.razorpay.com` already runs in the page, so this trusts no new party.
  - The changed policy was tried on the live site before deploying it: the order page was served with the new header, and a second test payment (order `0c0eeff4`) showed **0 violations** (`pay-candidate-csp.txt`). It is deployed when this part merges.
- **Real webhook delivery: verified afterwards; see the addendum.** At the time it was waiting for the owner to add the webhook in Razorpay's dashboard (test mode). Both payments above found no webhook events within 60 s, as expected while none is configured. The check is the `pay-webhook` part of the same script: a `payment.captured` or `order.paid` event recorded as APPLIED or ALREADY_RECORDED, which requires a valid signature and the `X-Razorpay-Event-Id` header.

## 5. Stop and start, and data

[`cloud/stop-start.txt`](2026-10-12-checkpoint-7/cloud/stop-start.txt), [`cloud/recheck-after-stop-start.txt`](2026-10-12-checkpoint-7/cloud/recheck-after-stop-start.txt)

- **`demo.sh stop`** (09:44 UTC):
  - it backed the database up to `s3://one3d-demo-ops-…/backups/one3d-20261012T094407Z.dump` (66 KB);
  - it stopped the containers, then the instance;
  - while stopped, the site did not answer (HTTP 000).
- **`demo.sh start`** (09:45 UTC):
  - it started the instance and redeployed the recorded tag;
  - the certificate was not due for renewal;
  - it answered 200 at 09:46:18, after five 502s while the backend started.
- **Afterwards:** order `d0653b83` and file `3f45dde5` read back unchanged through the site (AWAITING_PAYMENT; VERIFIED).
- **Later stops** each wrote another backup (for example `one3d-20261012T100933Z.dump`).

## 6. Memory budget (t3.small, 2 GiB)

- **Locally, under the Checkpoint 5 workload** (recorded with part 1): peaks were backend 423 of 640 MiB and simulator 137 of 192 MiB, and nothing was killed.
- **On the instance, idle after the cloud checks** ([`cloud/replace-containers.txt`](2026-10-12-checkpoint-7/cloud/replace-containers.txt) §1):
  - containers: backend 339 MiB, frontend 96, database 60, simulator 48, Nginx 5, about 550 MiB in total;
  - the host: 837 MiB used, 846 MiB available, plus 1 GiB of swap.
- **Disks:** the root volume is 34% used (4.0 of 12 GB), the data volume 3% (110 MB of 4 GB).
- **Headroom:** whether the Checkpoint 8 monitoring stack also fits is measured then.

## 7. Cost and runtime so far

- **Estimate** (decision 0003): about $0.18 a day stopped and $0.72 a day running, and about $43 of One3D's $55 credit allocation to 2027-04-30.
- **Budgets:** `Account-Credit-Use` ($15 a month, before credits) and `Account-Out-Of-Pocket` ($1 a month, after credits) exist next to the unchanged OneCreations budget (`identity-boundaries.txt`).
- **Runtime on 2026-10-12** (UTC; the deployment user cannot read CloudTrail, so these come from the volume creation time and the scripts' logs):
  - 08:23–09:44, the first apply, deploy and checks;
  - 09:45–09:51;
  - 10:01–10:09.
  - About 1.6 instance-hours, roughly $0.04 of EC2 time on top of the standing cost.
- **Actual billed cost:** not visible yet. It is recorded in Checkpoint 8.

## 8. Notes from implementation

- **GitHub's OIDC subject for this repository includes numeric ids** (`repo:harsha135135@<owner id>/One3D@<repository id>:ref:refs/heads/main`). The first `main` run could not assume the deploy role, because the trust named `repo:harsha135135/One3D:…`. The trust now matches the exact subject (`b2d4044`), and the rerun deployed. The failure also showed that the trust is matched exactly.
- **`aws login` and a browser root session:**
  - with the root console session still signed in, `aws login` stored root credentials under the deployer's profile. The runbook now says to sign out first;
  - the user also needs `SignInLocalDevelopmentAccess` for `aws login`, and the policy's region deny makes an exception for `signin`.
- **Security group rule descriptions cannot contain an apostrophe.**
- **Cognito accepts each TOTP code once.** The check script waits for a new 30-second step before signing the same user in again.
- **The first run of the files check** reported 0 CSP violations "on the catalog, custom part, orders and requests pages". Its record of violations started empty on every page load, so that line covered only the last page (requests). The `csp` part checks each page separately and found none, and the files check now gathers every page. The first screenshot showed the requests page and was renamed to `customer-requests.png`.
- **The clean checkout used Docker's layer cache** from earlier builds on the same machine. CI builds the images on fresh runners from the repository alone, with only its own GitHub Actions layer cache.

## Owed

Nothing. Real webhook delivery, the rollback run and the live CSP change were completed afterwards; see the two addenda.

## Known limitations

- **No branch protection.** GitHub does not offer it for this private repository on this plan, so merging only green PRs is a process rule; promotion itself is enforced (§2).
- **The ECR token is stored on the instance.** `docker login` stores the 12-hour ECR token unencrypted in `/root/.docker/config.json`. Only root on the instance can read it, and there is no SSH.
- **Sessions are held in the backend's memory.** Replacing the backend container or restarting the instance signs everyone out.
- **About 15 seconds of 502 after every start or backend replacement,** while the JVM starts. There is no second instance.
- **Cognito MFA is set for the whole pool,** so demo customers also need an authenticator app.
- **While stopped, the demo costs about $0.18 a day** for the Elastic IP and the disks. Only destroying it stops that.
- **Razorpay retries a webhook it could not deliver and disables it after about a day of failures.** While the demo is stopped, deliveries fail. Do not stop the demo in the minutes after a payment, and after a long stop check in the dashboard that the webhook is still enabled. A payment whose webhook never arrived is still confirmed by the order page's own check with Razorpay.

## Addendum: after PR #10 merged (2026-10-12, 10:27–10:41 UTC)

PR #10 merged as `dc26125`. CI on `main` ([run 38236403998](https://github.com/harsha135135/One3D/actions/runs/38236403998)) passed, pushed the images and skipped the deploy, because the demo was stopped. `demo.sh start` (10:31 UTC) then redeployed `70ef808` and deployed `dc26125`.

**Real webhook delivery from Razorpay: verified.** [`cloud/pay-webhook.txt`](2026-10-12-checkpoint-7/cloud/pay-webhook.txt), [`cloud/webhook-event-ids.txt`](2026-10-12-checkpoint-7/cloud/webhook-event-ids.txt)
- **The payment:** with the owner's test-mode webhook in place, the customer paid order `756cf6bf` in hosted Checkout.
- **Razorpay delivered three signed events within seconds:**
  - `payment.authorized` was IGNORED, because an authorization alone never confirms;
  - `payment.captured` was **APPLIED**, and the payment is recorded with source **WEBHOOK**. The webhook arrived before the order page's own check;
  - `order.paid` was ALREADY_RECORDED.
- **Event ids:** each delivery stored its `X-Razorpay-Event-Id`, and the endpoint refuses a delivery without one.
- **The order** is CONFIRMED by `RAZORPAY_TEST`.
- **CSP:** the old policy's one violation (Razorpay's risk-detection script) appeared again, because the CSP change was not live; see below.

**The first rollback run found a deploy defect.** [`cloud/rollback-first-run.txt`](2026-10-12-checkpoint-7/cloud/rollback-first-run.txt), [`cloud/nginx-not-recreated.txt`](2026-10-12-checkpoint-7/cloud/nginx-not-recreated.txt)
- **What differs between the revisions:** `dc26125` and `70ef808` have byte-identical images (same digests in all three repositories), and no migration lies between them. They differ only in the deploy bundle's Nginx template (the CSP).
- **The run itself:** `deploy.yml` deployed `dc26125`, then `70ef808`, then `dc26125`. Each run was green, and the instance's `deployed-tag` and `previous-tag` followed.
- **The site never changed:** it served the `70ef808` policy throughout.
  - Nginx renders its templates only when its container starts, and Compose recreates a container only when its own configuration changes.
  - So a deploy that changed only the templates left the old configuration running, while recording the new commit as deployed.
- **The first version of the check missed it.** It looked for `cdn.razorpay.com` anywhere in the header, and `img-src` allows it in both revisions. The check now reads `script-src`, Nginx's rendered config and the container's start time.
- **Fix (this follow-up):** Nginx is labelled with a hash of its templates (set by `deploy.sh`), so a deploy that changes them recreates Nginx. A bundle without the label, such as `70ef808`'s, also recreates it. This was checked locally with Compose: the same hash leaves the container running, a new hash recreates it with the new template, and removing the label recreates it.
- **After this follow-up merges,** the rollback run is repeated (to `70ef808` and forward again), and the CSP change is checked live. Until then, Checkpoint 7's rollback item stays owed.

**Runtime:** 10:31–10:40 UTC, then stopped again with a backup.

## Addendum 2: rollback and the CSP change, after the fix (2026-10-12, 10:56–11:04 UTC)

The fix merged as `15ff7c2` (PR #11; the fix itself is `8b1837d`). CI on `main` ([run 38239091599](https://github.com/harsha135135/One3D/actions/runs/38239091599)) passed and skipped the deploy, because the demo was stopped.

**Deploy, revert and deploy again: passed.** [`cloud/rollback.txt`](2026-10-12-checkpoint-7/cloud/rollback.txt) ([script](2026-10-12-checkpoint-7/cloud/rollback.sh))

| Step | Run | Instance | Live `script-src` | Nginx |
|---|---|---|---|---|
| `demo.sh start` deploys `15ff7c2` | [38239605669](https://github.com/harsha135135/One3D/actions/runs/38239605669) | deployed `15ff7c2`, previous `dc26125` | with `https://cdn.razorpay.com` | started 10:58:32, during this deploy |
| Roll back to `70ef808` | [38239757583](https://github.com/harsha135135/One3D/actions/runs/38239757583) | deployed `70ef808`, previous `15ff7c2` | without it (the old policy) | started 10:59:58, after the dispatch at 10:59:23 |
| Forward again to `15ff7c2` | [38239910658](https://github.com/harsha135135/One3D/actions/runs/38239910658) | deployed `15ff7c2`, previous `70ef808` | with it | started 11:01:19, after the dispatch at 11:00:43 |

- **What was checked after each step:** the live header, Nginx's rendered config and the tag files, read from outside and through SSM. The site answered 200 after each.
- **What was and was not exercised:** the images are the same in both revisions (same digests), and there is no migration between them. So these runs show the deploy mechanism changing what serves the site, here through the Nginx configuration.
  - Both live transitions recreated Nginx because the label was added or removed.
  - The case later deploys will hit, a labelled hash that changes, was checked only locally with Compose.
  - A deploy between two revisions from before the fix (both without the label) still leaves Nginx as it is. The runbook says to stop and start the demo in that case.

**CSP change live: checked.** [`cloud/pay-after-fix.txt`](2026-10-12-checkpoint-7/cloud/pay-after-fix.txt)
- **The payment:** order `5210e7fc` was paid in hosted Checkout under the deployed policy, with **0 CSP violations**. Razorpay's risk-detection script now loads.
- **The webhook confirmed it again:** `payment.captured` APPLIED (source `WEBHOOK`), `order.paid` ALREADY_RECORDED, `payment.authorized` IGNORED.

**Deployed revision:** `15ff7c2`. **Runtime:** 10:56–11:04 UTC, then stopped with a backup (`one3d-20261012T110314Z.dump`).
