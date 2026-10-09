# Roadmap: finish V1, present the evidence, then expand

Status: planned; no checkpoint has passed · Last reviewed: 2026-10-07

## 1. How to use this roadmap

The immediate objective is a fresher backend portfolio project that Harsha understands and can demonstrate. Business automation comes later.

[V1 scope](v1-scope.md) is the scope authority. [ARCHITECTURE.md](../ARCHITECTURE.md) defines the technical structure. Record actual progress in [IMPLEMENTED.md](../IMPLEMENTED.md) and claims in [resume-evidence.md](resume-evidence.md).

The earlier calendar-driven business roadmap is preserved in [history](history/roadmap-2026-09-30.md). Its printer integration, sustainability, certification and business experiments are not V1 requirements.

Advance through working checkpoints rather than a fixed twelve-week promise. A checkpoint passes after its behavior is demonstrated and Harsha completes its ownership exercise. Time spent understanding a critical concept is preferable to checking off unexplained code.

Each checkpoint leaves a runnable application, focused changes, relevant tests and an evidence entry. Nothing below is completed merely because it appears in the plan.

## 2. First runnable milestone: one order through one simulator

This milestone is smaller than full V1. Run locally with seeded demo identities and clearly simulated payment. Do not expose an unauthenticated prototype publicly.

### Checkpoint 1 — API, database and catalog order

**Build:** Spring application, PostgreSQL/Flyway, one seeded product/configuration and recipe, REST creation/retrieval of a one-line order, validation, consistent errors and an API reference/test collection.

**Demonstrate:**

- Create a fresh database from migrations.
- Reject invalid input with a useful response.
- Change catalog data without rewriting an existing order snapshot.
- Retrieve the order after restarting the application.

**Ownership exercise:** trace HTTP → controller → service → SQL; add a validation rule and test; explain the schema and snapshot.

### Checkpoint 2 — Job, attempt, bed and QC

**Build:** an explicit local demo-only eligibility action, operator release, one job, one simulated printer, saved attempts, manual assignment, failure/completion, clearance and whole-part QC. A simple API-driven operator demo is sufficient here.

**Demonstrate:**

- An order passes release → print → QC → ready for handoff.
- Failure preserves attempt 1; retry creates attempt 2.
- Completion or failure does not automatically clear the bed.
- A QC failure prevents readiness for handoff.

**Ownership exercise:** explain why order, job and attempt have different states; change a transition rule and test an invalid transition.

**First milestone exit:** Harsha can show an end-to-end backend. This is progress, not “V1 complete.”

## 3. Complete the bounded application

### Checkpoint 3 — Concurrency and durable simulator

**Build:** atomic reservations, protection against competing active ownership, stable command IDs, durable journal, independent simulator restart, failure injection and uncertain-start reconciliation.

**Demonstrate:**

- Two concurrent assignments cannot both reserve the printer.
- Repeating the same command produces one execution.
- A lost start acknowledgement creates an uncertain state and blocks reassignment.
- Backend/simulator restarts retain enough evidence to reconcile.
- Document what resolves uncertainty and what still requires operator action.

**Ownership exercise:** reproduce a race or duplicate; locate its protecting transaction/constraint; explain why timeout is not proof of failure.

### Checkpoint 4 — Identity and bounded frontend

**Build:** the chosen authentication integration, backend-enforced customer/operator roles, customer order views and an operator queue. Include responsive forms and loading/empty/error states.

**Demonstrate:**

- Customer A cannot read Customer B's records even with their IDs.
- Customers cannot directly call operator-only endpoints.
- Invalid/expired authentication does not expose protected data.
- The operator UI relies on permissions, not a secret URL.
- Forms are usable with a keyboard and on a phone-sized viewport.

**Ownership exercise:** explain authentication versus authorization; locate role and record-ownership checks; diagnose a rejected request.

### Checkpoint 5 — Private STL request and manual quote

**Build:** one STL/revision with the 20 MB limit, authorized direct storage transfer, exact artifact reference/checksum, requirements form, review, quote/expiry, acceptance and frozen order scope. Preparation remains manual outside the application.

**Demonstrate:**

- Unauthorized downloads and oversized uploads are rejected.
- Replacing the model creates a new submission without inherited approval.
- Accepted scope identifies exact requirements, artifact and recipe revision.
- Expired quotes cannot be newly accepted.
- Repeated acceptance does not create another order.

**Ownership exercise:** trace browser → storage → metadata; distinguish key/version/checksum/signed access; change a quote rule and test it.

### Checkpoint 6 — Payment sandbox and full journey

**Build:** one test-mode provider, signature verification, amount/currency/order checks, durable event/payment identities, manual production release and handoff, and customer progress.

Remove or strictly confine the earlier demo-only paid action so it cannot bypass the hosted payment workflow.

**Demonstrate:**

- Catalog and custom-quote paths reach the same production workflow.
- A forged callback or wrong amount cannot mark an order paid.
- Duplicate/reordered delivery cannot regress state or release extra production.
- A second distinct payment or ambiguous result is flagged for investigation.
- Payment alone cannot dispatch the printer.
- QC/handoff produce the corresponding customer timeline facts.

**Ownership exercise:** explain signatures, event deduplication versus payment deduplication and the transaction boundary; replay a sanitized test event.

## 4. Prove deployment and operations at a simple level

### Checkpoint 7 — Docker, AWS, Nginx and one CI pipeline

**Build:** documented Compose setup and persistent volumes; one small EC2 demo with private S3/access controls; Nginx HTTPS/routing; one GitHub Actions check/build/controlled-deploy pipeline; safe demo data and private internal ports.

**Demonstrate:**

- Start from a clean checkout using documented steps.
- Replace containers without losing expected persisted records.
- Block promotion when a required test fails.
- Deploy a known image revision and document how to revert it.
- Verify HTTPS routing and that database, backend and operational ports are not publicly exposed.

**Ownership exercise:** explain images/containers/volumes, trace proxy routing, diagnose a CI failure and explain each component's AWS permissions.

### Checkpoint 8 — Terraform, monitoring and recovery

**Build:** small Terraform configuration for declared demo resources with manual prerequisites documented, safe state handling, limited Prometheus metrics, one Grafana dashboard, structured logs, backup/restore and cleanup instructions.

**Demonstrate:**

- Inspect a plan and recreate a disposable environment once without destroying valuable data.
- Explain any changes in a follow-up plan.
- Inject an API error or stalled task; identify it through metrics plus logs.
- Restore a backup into a separate database and verify representative records/relationships.
- Record resource size, runtime/cost observation window and cleanup result; do not invent savings or throughput claims.

**Ownership exercise:** explain state, latency/error metrics, label cardinality, a recovery limitation and the cost of idle cloud resources.

These are small exercises around one app. Optional brokers, Kubernetes and extra CI systems do not belong in this checkpoint.

## 5. Final checkpoint: release, ownership and résumé

### Checkpoint 9 — V1 release review

1. Run recorded acceptance scenarios against a known code revision.
2. Reconcile current architecture, setup and IMPLEMENTED.md with actual behavior.
3. Walk through the [completion gate](v1-scope.md#6-v1-completion-gate).
4. Harsha explains and modifies critical paths; unresolved understanding gaps remain unfinished work.
5. Prepare a stable release reference, short demo and evidence-backed résumé draft.
6. Harsha personally signs off on completion and updates the résumé using verified claims.
7. Begin applications/interview preparation. Pause new features until Harsha selects the next increment.

An assistant may report that the evidence is ready for review. It must not invent Harsha's signoff or continue to V2 on its own. Full business launch and optional labs are not release conditions.

### Five-minute V1 demo

| Time | Show |
|---|---|
| 0:00–0:45 | Problem, scope and architecture; identify simulated printer and test payment |
| 0:45–1:30 | Request, reviewed revision, quote and accepted order; use prepared fixtures where needed |
| 1:30–2:15 | Verified test payment and separate operator release |
| 2:15–3:15 | Duplicate command and lost acknowledgement; why uncertain work stays held |
| 3:15–4:00 | Reconciliation, bed clearance, QC and handoff |
| 4:00–5:00 | Test, CI, deployment and dashboard/restore evidence; explain limitations |

Keep an offline recording and sanitized fixtures so a temporary cloud or sandbox outage does not prevent explaining the project. A recording supports the demo; it does not replace runnable code or understanding.

## 6. Optional labs after V1

Select one for a specific learning goal or target role. Each needs a bounded exercise, failure case, cleanup instructions and its own evidence entry.

Suggested order if relevant: RabbitMQ notification worker → Ansible setup or Jenkins CI comparison → local Kubernetes → Kafka replay.

There is no obligation to complete this list. Labs stay separate unless Harsha explicitly approves an integration scope. Describe actual lab experience on the résumé rather than claiming those tools powered V1.

## 7. V2 and later: business-driven increments

V2 begins after the V1 résumé checkpoint and a new request from Harsha. Reuse sound code and boundaries; change simplified assumptions through deliberate migrations and tests.

| Candidate increment | Evidence that justifies it | First bounded delivery |
|---|---|---|
| Richer custom work | Actual requests exceed V1 limits | Larger reliable uploads and multi-file requirements with constrained validation |
| Multi-part manufacturing | A real product requires multiple plates/components | Required quantities, accepted output and assembly prerequisites for one product |
| Real printer integration | Supported interface and supervised recovery are verified | Read-only monitoring, then separately approved supervised dispatch |
| Small-farm scheduling | Multiple machines and competing work exist | Manual assignment aided by an explainable queue policy |
| Paid business operation | Commercial, privacy, support and recovery requirements are ready | Live payments and a narrow offer; reassess hosting resilience |
| Inventory/costing | Reliable actual production data exists | Material consumption and operator effort for one repeatable process |
| Broker/richer telemetry | Current transport has a measured limitation | One justified integration with failure/recovery tests |
| Bed automation | Measured operator delay supports the investment | One qualified workflow with explicit failure handling |
| PrintBack/recycling | Real collection, funding and material routes exist | Separate bounded pilot; never a V1 dependency |

Define the business problem, scope, risks, demo and completion gate for each increment before implementation. Revisit résumé claims after each completed increment. Do not migrate to microservices, Kubernetes or managed messaging just to make V2 look more complex.
