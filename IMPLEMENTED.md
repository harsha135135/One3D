# Implementation status

Last reviewed: 2026-10-09

**Current state: Checkpoint 1 complete; Harsha signed it off on 2026-10-09.** The catalog order API (commit `0fb442e`) passed its automated checks and a local run/restart demonstration on 2026-10-07. Harsha's quantity-default change (`482dbd6`) passed 29 tests on 2026-10-09. The schema and snapshot explanation in his review was reference-assisted and is to be revisited independently. Checkpoint 2 (release, jobs, attempts, simulated printer, bed clearance, QC, at most 3 attempts per job) is implemented and tested locally (commit `700256d` on `feat/checkpoint-2`). On 2026-10-09, 67 tests passed in the first run, and 69 passed after the attempt limit was added, including on the commit itself. The attempt limit was implemented by an assistant and accepted by Harsha subject to his final review. Its owner review is pending. A fix merged after Checkpoint 2 rejects fractional quantities (73 tests). Since 2026-10-09 the assistant implements the remaining checkpoints, and owner reviews are deferred until V1 is complete; see "Process change" below. Checkpoints 3–9 have not started. There is no deployed environment and no accepted V1 release.

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
| 3–9 | Not started | — | — | — |

## Required V1 application work

| Area | Design | Implementation | Verification / evidence | Owner review |
|---|---|---|---|---|
| Java modules, PostgreSQL schema and migrations | Defined in architecture | Partial: `catalog`, `orders`, `common` modules; Flyway V1–V2 (`0fb442e`); `production` module and V3–V4 (CP2) | CP1 checks passed: [CP1 evidence](docs/evidence/2026-10-07-checkpoint-1.md) | CP1 part signed off 2026-10-09; schema explanation reference-assisted, to revisit |
| Customer/operator identity, authorization and ownership | Defined | Not started | Pending | Pending |
| One catalog product and bounded custom STL submission | Defined | Partial: seeded catalog product and snapshot order (`0fb442e`); STL submission not started | Catalog part tested: [CP1 evidence](docs/evidence/2026-10-07-checkpoint-1.md) | Catalog part signed off 2026-10-09; STL pending |
| Immutable revision, manual review, quote and approval | Defined | Not started | Pending | Pending |
| Sandbox payment verification and duplicate handling | Defined | Not started | Pending | Pending |
| Production release, job/attempt records and simulator | Defined | Partial (CP2): operator release, one job per order, saved attempts, one simulated printer driven by API calls. The separate restartable simulator is CP3 | [CP2 evidence](docs/evidence/2026-10-09-checkpoint-2.md) | Pending |
| Failure recovery, uncertain start, bed-clear and whole-part QC | Defined | Partial (CP2): failed attempts kept, operator-started retry up to 3 attempts per job, bed clearance, whole-part QC. A job that uses all 3 stays `RELEASED` with no override. Uncertain start and concurrency are CP3 | [CP2 evidence](docs/evidence/2026-10-09-checkpoint-2.md) | Pending |
| Minimal customer and operator UI | Defined | Not started | Pending | Pending |
| Integration/security tests and repeatable demonstration | Acceptance specified | Partial: CP1 PostgreSQL integration tests; no security tests yet | [CP1 evidence](docs/evidence/2026-10-07-checkpoint-1.md) | CP1 part signed off 2026-10-09 |
| Runnable setup, API documentation and troubleshooting notes | Acceptance specified | Partial: CP1 local setup in README, `backend/api.http` | Commands verified in [CP1 evidence](docs/evidence/2026-10-07-checkpoint-1.md) | CP1 part signed off 2026-10-09 |

## Required tool demonstrations

These are small hands-on demonstrations, not claims of enterprise operations.

| Tool / task | Implementation | Evidence | Limits |
|---|---|---|---|
| Docker Compose and persisted application data | Partial: `compose.yaml` runs the local PostgreSQL only, on 127.0.0.1 | Pending (container recreation not yet tested) | One local environment |
| AWS EC2, private S3 and scoped IAM | Not started | Pending | One small demo environment |
| Cognito identity integration | Not started | Pending | Customer and operator roles |
| Nginx HTTPS and routing | Not started | Pending | Same-origin application/API |
| GitHub Actions | Not started | Pending | One primary CI pipeline |
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
