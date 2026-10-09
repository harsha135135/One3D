# Implementation status

Last reviewed: 2026-10-09

**Current state: Checkpoint 1 complete; Harsha signed it off on 2026-10-09.** The catalog order API (commit `0fb442e`) passed its automated checks and a local run/restart demonstration on 2026-10-07. Harsha's quantity-default change (`482dbd6`) passed 29 tests on 2026-10-09. The schema and snapshot explanation in his review was reference-assisted and is to be revisited independently. Checkpoints 2–9 have not started. There is no deployed environment and no accepted V1 release.

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
| 2–9 | Not started | — | — | — |

## Required V1 application work

| Area | Design | Implementation | Verification / evidence | Owner review |
|---|---|---|---|---|
| Java modules, PostgreSQL schema and migrations | Defined in architecture | Partial: `catalog`, `orders`, `common` modules; Flyway V1–V2 (`0fb442e`) | CP1 checks passed: [CP1 evidence](docs/evidence/2026-10-07-checkpoint-1.md) | CP1 part signed off 2026-10-09; schema explanation reference-assisted, to revisit |
| Customer/operator identity, authorization and ownership | Defined | Not started | Pending | Pending |
| One catalog product and bounded custom STL submission | Defined | Partial: seeded catalog product and snapshot order (`0fb442e`); STL submission not started | Catalog part tested: [CP1 evidence](docs/evidence/2026-10-07-checkpoint-1.md) | Catalog part signed off 2026-10-09; STL pending |
| Immutable revision, manual review, quote and approval | Defined | Not started | Pending | Pending |
| Sandbox payment verification and duplicate handling | Defined | Not started | Pending | Pending |
| Production release, job/attempt records and simulator | Defined | Not started | Pending | Pending |
| Failure recovery, uncertain start, bed-clear and whole-part QC | Defined | Not started | Pending | Pending |
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
