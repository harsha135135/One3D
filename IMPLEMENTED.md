# Implementation status

Last reviewed: 2026-10-12

**Current state: Checkpoint 1 complete; Harsha signed it off on 2026-10-09.** The catalog order API (commit `0fb442e`) passed its automated checks and a local run/restart demonstration on 2026-10-07. Harsha's quantity-default change (`482dbd6`) passed 29 tests on 2026-10-09. The schema and snapshot explanation in his review was reference-assisted and is to be revisited independently. Checkpoint 2 (release, jobs, attempts, simulated printer, bed clearance, QC, at most 3 attempts per job) is implemented and tested locally (commit `700256d` on `feat/checkpoint-2`). On 2026-10-09, 67 tests passed in the first run, and 69 passed after the attempt limit was added, including on the commit itself. The attempt limit was implemented by an assistant and accepted by Harsha subject to his final review. Its owner review is pending. A fix merged after Checkpoint 2 rejects fractional quantities (73 tests). Since 2026-10-09 the assistant implements the remaining checkpoints, and owner reviews are deferred until V1 is complete; see "Process change" below. Checkpoint 3 (a separate durable printer simulator, dispatch through a durable work queue, reconciliation of uncertain starts, bed clearances bound to the print they clear, and protection against concurrent operators) is implemented and tested: simulator 14 tests and backend 105 tests passed on 2026-10-09, and CI runs both on every pull request. Its owner review is deferred. Checkpoint 4 (OIDC sign-in, CUSTOMER/OPERATOR roles, order ownership, CSRF protection and a Next.js customer and operator frontend) is implemented and tested: backend 121 tests, frontend lint, types, build and 12 browser smoke runs, plus a real-stack sign-in script; its owner review is deferred. Checkpoint 5 (private STL upload to versioned storage with a pinned version and SHA-256, immutable submission revisions, operator review, manual quotes with expiry, and acceptance that creates exactly one order) is implemented and tested against PostgreSQL and S3Mock: backend 182 tests, frontend 20 browser smoke runs, a real-stack script and a real browser run; its owner review is deferred. Checkpoint 6 (Razorpay test-mode checkout; orders confirmed only by verified, captured payments from a signed webhook or the backend's own check with Razorpay; event and payment deduplication; manual review of inconsistent payments; handoff and a customer timeline) is implemented and tested: backend 227 tests, 26 browser smoke runs, and two real test-mode payments through Razorpay's hosted Checkout; real webhook delivery from Razorpay is owed after the deploy; its owner review is deferred. Checkpoints 7–9 have not started. There is no deployed environment and no accepted V1 release.

## Process change (owner instruction, 2026-10-09)

After Checkpoint 2, Harsha asked the assistant to implement the rest of V1 (Checkpoints 3–9) by following `ARCHITECTURE.md`, `docs/v1-scope.md` and `docs/roadmap.md`. He will study the repository after V1 is implemented, and does not want per-checkpoint reviews or exercises until then. The assistant implements each checkpoint, verifies it, records evidence, and merges its own PR once the checks pass.

Consequences, under the "latest owner instruction" rule in `AGENTS.md`:
- Each checkpoint from 2 onwards is recorded as **"implemented and tested; owner review deferred"**, never as passed. The roadmap defines "passed" to include Harsha's ownership exercise.
- Evidence records state that the work was assistant-implemented. Git authorship and the no-attribution rule are unchanged.
- V1 acceptance, Harsha's signoff, any release tag and any résumé claim remain his decisions. The final status before his review is **"implementation ready; personal review and signoff pending"**.
- External steps still need Harsha: AWS login, spend approval, Razorpay test keys and the `demo.one3d.store` DNS record.

The tables below are a progress record, not a feature request list. [V1 scope](docs/v1-scope.md) is authoritative. Update a row only when its evidence exists.

## How to record status

Track these facts separately:

- **Designed:** a contract or decision is written down.
- **Implemented:** source code/configuration exists; include the commit or reviewed diff.
- **Tested:** named checks ran, with date, command, environment and actual outcome.
- **Deployed:** the identified revision ran in the named environment. Local execution is not an AWS deployment.
- **Used in real production:** real business use is evidenced. This is outside V1 and is not implied by deployment.
- **Owner review:** Harsha explained, changed and debugged the relevant behavior. Automated checks cannot substitute for this.

A fact in one column does not establish the others. “Pending” means no verified evidence has been recorded; it is not a pass. Store records using [the evidence template](docs/resume-evidence.md#evidence-record-template), and link them below.

## Checkpoint progress

| Checkpoint | Implemented | Tested | Evidence | Owner review |
|---|---|---|---|---|
| 1 — API, database and catalog order | Yes, `0fb442e`; quantity default `482dbd6` (owner's change) on `feat/checkpoint-1` | 2026-10-07: `./mvnw clean verify`, 27 tests passed (3 unit, 24 PostgreSQL integration); local restart demo. 2026-10-09: `./mvnw clean verify` on `482dbd6`, 29 passed (3 unit, 26 integration) | [CP1 evidence](docs/evidence/2026-10-07-checkpoint-1.md) | **Signed off 2026-10-09.** Change and runtime trace independent; schema and snapshot explanation reference-assisted ([addendum](docs/evidence/2026-10-07-checkpoint-1.md#owner-review-addendum-2026-10-09)) |
| 2 — Job, attempt, bed and QC | Yes, `700256d` on `feat/checkpoint-2`. Attempt limit (`MAX_ATTEMPTS = 3`) implemented by an assistant | 2026-10-09: first run 67 passed (14 unit, 53 integration); after the attempt limit 69 passed (15 unit, 54 integration), also on `700256d`; three negative controls; local demo with the `demo` profile (before the limit) | [CP2 evidence](docs/evidence/2026-10-09-checkpoint-2.md) | Pending; deferred by owner until V1 is complete (2026-10-09) |
| Fix — integer quantity | Yes, `b8e19cc`: fractional quantities (1.5, 1.0, 1e0) return 400 instead of being truncated to 1. Implemented by the assistant | 2026-10-09: `./mvnw clean verify`, 73 passed (15 unit, 58 integration); negative control | [fix evidence](docs/evidence/2026-10-09-integer-quantity-fix.md) | Deferred by owner until V1 is complete |
| 3 — Concurrency and durable simulator | Yes: `45c9103` (simulator), `45f9791` (backend), `7f34bef` (Compose, CI) on `feat/checkpoint-3`. Implemented by the assistant | 2026-10-09: simulator `./mvnw clean verify` 14 passed; backend `./mvnw clean verify` 105 passed (19 unit, 86 integration); 9 negative-control runs; local demo with restart, lost reply and a concurrent start | [CP3 evidence](docs/evidence/2026-10-09-checkpoint-3.md) | Deferred by owner until V1 is complete |
| 4 — Identity and bounded frontend | Yes: `b038390` (backend), `5e528cc` (frontend), `9548341` (identity provider in Compose, CI) on `feat/checkpoint-4`. Implemented by the assistant | 2026-10-09: backend 121 passed (26 unit, 95 integration); frontend lint, build, types, Playwright 12/12; 5 negative controls; real-stack sign-in and security script | [CP4 evidence](docs/evidence/2026-10-09-checkpoint-4.md) | Deferred by owner until V1 is complete |
| 5 — Private STL request and manual quote | Yes: `661f106` (background-work refactor, V7), `22baae8` (files), `4b01fed` (requests, quotes, custom orders), `93f334d` (frontend, Compose storage), `9feb684`, `d39898e` on `feat/checkpoint-5`. Implemented by the assistant | 2026-10-10: backend 182 passed (34 unit, 148 integration, with S3Mock); frontend lint, build, types, Playwright 20/20; 9 new negative controls plus Checkpoint 3 controls B and A3 rerun after the refactor; real-stack script (20 MB upload through `:3000`) and a real browser run | [CP5 evidence](docs/evidence/2026-10-10-checkpoint-5.md) | Deferred by owner until V1 is complete |
| 6 — Payment sandbox and full journey | Yes: `9ac75e9` (backend), `523489a` (frontend), `9c9723d` on `feat/checkpoint-6`. Implemented by the assistant | 2026-10-12: backend 227 passed (46 unit, 181 integration); frontend lint, build, types, Playwright 26/26; 9 negative controls; real stack with two real Razorpay test-mode payments in hosted Checkout, signed webhook replays, both order paths to handoff | [CP6 evidence](docs/evidence/2026-10-12-checkpoint-6.md) | Deferred by owner until V1 is complete |
| 7–9 | Not started | — | — | — |

## Required V1 application work

| Area | Design | Implementation | Verification / evidence | Owner review |
|---|---|---|---|---|
| Java modules, PostgreSQL schema and migrations | Defined in architecture | Partial: `catalog`, `orders`, `common` modules; Flyway V1–V2 (`0fb442e`); `production` module and V3–V4 (CP2); V5 (CP3); `identity` and V6 (CP4); `background`, `files`, `requests`, `quotes` and V7 (CP5); `payments` and V8 (CP6) | CP1 checks passed: [CP1 evidence](docs/evidence/2026-10-07-checkpoint-1.md) | CP1 part signed off 2026-10-09; schema explanation reference-assisted, to revisit |
| Customer/operator identity, authorization and ownership | Defined | Implemented (CP4): OIDC login with server sessions; users by (issuer, subject); CUSTOMER/OPERATOR from provider groups; `/api/ops/**` operators only; ownership in queries; CSRF on every state change; problem-JSON 401/403 | [CP4 evidence](docs/evidence/2026-10-09-checkpoint-4.md) | Deferred |
| One catalog product and bounded custom STL submission | Defined | Implemented: seeded catalog product and snapshot order (`0fb442e`); one STL of at most 20 MB per revision, uploaded straight to private versioned storage with a presigned PUT, pinned version, background structure check and SHA-256 (CP5) | [CP1 evidence](docs/evidence/2026-10-07-checkpoint-1.md), [CP5 evidence](docs/evidence/2026-10-10-checkpoint-5.md) (S3Mock locally; real S3 is CP7) | Catalog part signed off 2026-10-09; STL part deferred |
| Immutable revision, manual review, quote and approval | Defined | Implemented (CP5): immutable submission revisions; request changes, reject or quote; a quote prices the latest revision with its own recipe revision and expires; a new revision withdraws the open quote; acceptance creates exactly one order and repeating it returns the same order | [CP5 evidence](docs/evidence/2026-10-10-checkpoint-5.md) | Deferred |
| Sandbox payment verification and duplicate handling | Defined | Implemented (CP6): Razorpay test mode; server-set amount; signed webhooks and server-side checks; only captured payments of the right amount confirm; unique event and payment ids; unknown, mismatched, late and second payments kept for review; payment never releases production | [CP6 evidence](docs/evidence/2026-10-12-checkpoint-6.md) (real webhook delivery owed after deploy) | Deferred |
| Production release, job/attempt records and simulator | Defined | Partial (CP2–CP3): operator release, one job per order, saved attempts, a separate restartable simulator process with a SQLite journal, stable command ids, a durable work queue with claim tokens and leases | [CP3 evidence](docs/evidence/2026-10-09-checkpoint-3.md) | Deferred |
| Failure recovery, uncertain start, bed-clear and whole-part QC | Defined | Implemented (CP2–CP3): failed attempts kept; operator-started retry, up to 3 attempts that reached the printer; uncertain starts held as `RECONCILIATION_REQUIRED` until the simulator's journal answers; refused starts undone exactly; bed clearances name the print they clear; whole-part QC. A job that uses all 3 attempts stays `RELEASED` with no override | [CP3 evidence](docs/evidence/2026-10-09-checkpoint-3.md) | Deferred |
| Minimal customer and operator UI | Defined | Implemented (CP4–CP6): Next.js catalog, my orders, order page with test-mode payment and timeline, custom part and requests pages; operator queue (release, handoff), job, printers, request review and payment review pages; loading, empty and error states; keyboard use; 390 px layout | [CP4](docs/evidence/2026-10-09-checkpoint-4.md), [CP5](docs/evidence/2026-10-10-checkpoint-5.md), [CP6 evidence](docs/evidence/2026-10-12-checkpoint-6.md) | Deferred |
| Integration/security tests and repeatable demonstration | Acceptance specified | Partial: PostgreSQL, simulator and S3Mock integration tests; security tests over every /api route (anonymous and customer), ownership and CSRF; browser smoke tests; real-stack sign-in, custom-part and payment-journey scripts; signed-webhook and payment tests, and every production test pays through checkout and a signed webhook | [CP3](docs/evidence/2026-10-09-checkpoint-3.md), [CP4](docs/evidence/2026-10-09-checkpoint-4.md), [CP5](docs/evidence/2026-10-10-checkpoint-5.md), [CP6 evidence](docs/evidence/2026-10-12-checkpoint-6.md) | Deferred |
| Runnable setup, API documentation and troubleshooting notes | Acceptance specified | Partial: CP1 local setup in README, `backend/api.http` | Commands verified in [CP1 evidence](docs/evidence/2026-10-07-checkpoint-1.md) | CP1 part signed off 2026-10-09 |

## Required tool demonstrations

These are small hands-on demonstrations, not claims of enterprise operations.

| Tool / task | Implementation | Evidence | Limits |
|---|---|---|---|
| Docker Compose and persisted application data | Partial: `compose.yaml` runs PostgreSQL, the simulator, the mock identity provider and S3Mock file storage on 127.0.0.1 with named volumes; the simulator kept a running print across `docker compose restart` | [CP3 evidence](docs/evidence/2026-10-09-checkpoint-3.md) (simulator restart only; application containers are CP7) | One local environment |
| AWS EC2, private S3 and scoped IAM | Partial: the S3 adapter (AWS SDK v2, presigned PUT/GET, version pinning) is written and tested against S3Mock; the bucket, IAM and EC2 are CP7 | [CP5 evidence](docs/evidence/2026-10-10-checkpoint-5.md) (S3Mock only; signature, expiry and size enforcement by real S3 still owed) | One small demo environment |
| Cognito identity integration | Partial: the OIDC integration is provider-neutral and runs locally against a mock provider; the Cognito pool, groups and MFA arrive with the AWS environment (CP7) | [CP4 evidence](docs/evidence/2026-10-09-checkpoint-4.md) (local provider only) | Customer and operator roles |
| Nginx HTTPS and routing | Not started | Pending | Same-origin application/API |
| GitHub Actions | Partial: `.github/workflows/ci.yml` runs the simulator, backend and frontend checks on every PR and push to `main` | First run passed on PR #4 (run 37936876770: simulator 14 tests; backend 19 unit and 86 integration tests in 2:00 min); image build and deployment are CP7 | One primary CI pipeline |
| Terraform | Not started | Pending | Recreate and clean up that same environment |
| Prometheus and Grafana | Not started | Pending | A few metrics, one dashboard, one diagnosis |
| Backup and restore exercise | Not started | Pending | Prove database and referenced file recovery; no high-availability claim |

Deployed revision/environment: **none recorded**. Real production usage: **none; not required for V1**.

## Optional labs

Ansible, Jenkins, RabbitMQ, Kafka and local Kubernetes: **not started and not required for V1 acceptance**. If completed later, record each as a separate exercise with its own limits. Do not add them to the required completion percentage or call them application dependencies without a new scope decision.

## V1 acceptance record

| Gate | Status | Evidence / owner acknowledgement |
|---|---|---|
| All required scope checkpoints pass | Pending | — |
| Functional and failure demonstrations are repeatable | Pending | — |
| Security, CI, deployment and restore checks pass | Pending | — |
| Setup and documentation match the accepted revision | Pending | — |
| Owner can explain, change and debug critical paths | Pending | — |
| Harsha accepts the V1 release | Pending | — |
| Release revision and known limitations are recorded | Pending | — |
| Résumé is updated from verified evidence | Pending | — |
| Next V2 increment explicitly agreed | Pending; not part of V1 completion | — |

Do not set these gates to complete when only the planning documents have been updated.
