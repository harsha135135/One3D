# One3D interview defense and ownership review

Status: study guide; ownership exercises **pending**. Date: 2026-10-14.
Use [demo.md](demo.md) for the existing five-minute journey and recording. The live
application is a one-printer simulation with sandbox money, not an operating factory.

## A concise demonstration

| Time | Show | Explain |
|---|---|---|
| 0:00–0:40 | Architecture and limits | One Spring application, PostgreSQL, separate durable simulator; one product or one STL, quantity one. |
| 0:40–1:30 | Custom request → reviewed revision → quote → acceptance | Exact file version/checksum; manual approval; accepting once freezes scope. |
| 1:30–2:15 | Razorpay test checkout and confirmed order | The backend needs trusted captured-payment evidence; browser success is insufficient. |
| 2:15–3:20 | Operator release, start attempt and lost-ack fault | Payment does not send a print command; uncertainty retains ownership; reconcile the same command. |
| 3:20–4:10 | Simulator completion, QC and clearance | QC accepts the part; clearance makes the bed available; these are independent facts. |
| 4:10–5:00 | One regression, one metric, release evidence | Show a real failure/recovery trace; qualify test counts and explain what remains pending. |

Rehearse with separate customer and operator browser profiles. Use synthetic fixtures;
hide credentials, MFA enrollment QR codes, signed file URLs and customer identifiers.
Check access and runtime health in advance. If AWS is unavailable, show the existing
captioned recording or the local stack and state which environment is on screen.
Do not execute cleanup, replacement or database restoration during an interview.

## Source map and interview questions

Paths below are relative to the repository. For each row, open the implementation and
test together and trace one request without relying on a memorized architecture diagram.

| Topic and source | Questions to answer | Personal exercise |
|---|---|---|
| `backend/.../orders/OrderController.java`, `OrderService.placeOrder`, `CustomerOrder`, `OrderLine`; `OrderAtomicityIT`, `OrderSnapshotIT` | What does `@Transactional` cover? Why DTOs? What survives a catalog price edit? Why integer minor units? | Add a bounded validation rule, test rejection and prove failed creation leaves no partial order. |
| `quotes/QuoteService.accept`, `requests/RequestService.lockForAcceptance`; `QuotesIT`, V7 migrations | Why lock this row? What happens if acceptance races a new model revision? What guarantees one order? | Change the minimum payment window using a test clock; test just-before, exact-boundary and expired acceptance. |
| `files/FileService.finalizeUpload`, `ArtifactVerifier`, `ArtifactVerification.apply`; `FilesIT` | How do key, version and checksum differ? Who authorizes upload/download? Can a late verifier approve changed bytes? | Replace the uploaded object and prove the accepted quote still references its original version. |
| `identity/SecurityConfig`, `CurrentUser`, `GroupRoleMapper`; `SecurityIT` | Authentication vs authorization? Why `(issuer, subject)`? Why CSRF with cookies? When are group roles loaded? | Read another user's order/file ID and call an operator endpoint as a customer; trace every rejection. |
| `payments/RazorpayWebhookController.receive`, `WebhookSignature`, `RazorpayWebhooks.apply`, `PaymentRecorder.record`; `RazorpayWebhookIT` | Why sign the raw body? Event dedup vs payment dedup? What if the DB commit fails before the 2xx? | Deliver duplicate and concurrent signed events; prove one effect, then alter the signature and amount. |
| `production/ProductionService.startAttempt`, `background/PendingWorkStore.claim`, `DispatchWorker`, `DispatchOutcomes.apply`; `ConcurrencyIT`, `DispatchReconciliationIT` | What is durable before an external call? Lease vs claim token? What if an old worker finishes late? | Reproduce competing assignments and a stale result; point to the protecting constraint and transaction. |
| `simulator/.../PrinterSimulator`, `Journal`; simulator tests and `DispatchReconciliationIT` | Why an independent process and journal? Why isn't timeout failure? What if HTTP 200 contains an unknown command outcome? | Drop an acknowledgement, restart the backend, reconcile without a second start; explain the malformed-response regression. |
| `production/PrintAttempt`, `Printer`, `ProductionJob`; `BedClearanceIT`, `ProductionWorkflowIT` | Job vs attempt? QC vs bed clearance? Why bind clearance to an attempt? What happens after three attempts? | Make an invalid transition and verify both the error and unchanged persisted state. |
| `compose.yaml`, `compose.deploy.yaml`, `infra/nginx/https/default.conf.template` | Image vs container vs volume? Which ports are public? Where does TLS end? Why same-origin API routing? | Diagnose a wrong internal hostname or missing environment value locally; explain logs and fix. |
| `infra/terraform/demo/{compute,storage,identity,github}.tf` | EC2 vs EBS? S3 versioning vs backup? IMDS role credentials? What can CI do? What secrets can be in state? | Read a saved plan and identify every destruction/replacement; explain why it must not be applied blindly. |
| `infra/deploy/`, `infra/ops/`, `.github/workflows/` | Why SHA tags plus digests? Why a green job can skip deploy? Why image rollback may not undo migrations? | Reject a wrong release digest; restore a disposable DB and verify catalog/custom chains and exact artifact bytes. |
| `infra/monitoring/`, request-id logging and application metrics | Counter vs gauge? What can a dashboard prove? How do you investigate an uncertain job? | Inject a fault, capture its request/command ID, inspect metric and DB state, then reconcile and explain recovery. |

`backend/.../` means `backend/src/main/java/store/one3d/`; tests are in the corresponding
`backend/src/test/java/store/one3d/` package. Simulator sources are under
`simulator/src/main/java/store/one3d/simulator/`. Find classes with `rg --files` rather
than assuming a controller owns every business rule.

## Walk through the architecture in two minutes

1. Nginx routes customer/operator pages to Next.js and API/sign-in routes to Spring.
2. Spring Security completes Cognito OIDC login, then uses an application session.
   Services check resource ownership; the operator URL is not the permission boundary.
3. Controllers validate HTTP input. Services coordinate business rules and transactions.
   PostgreSQL constraints arbitrate concurrent changes; Flyway records schema versions.
4. Files go directly to private S3 with authorized, limited signed access. Reviewed
   revisions bind exact version IDs and digests to quotes and orders.
5. Sandbox checkout records provider identifiers. Signed events and provider queries
   confirm eligible captured payments. Manual review facts do not themselves implement
   a complete financial hold/resolution engine.
6. An operator releases confirmed scope. Durable work sends a stable command to a
   separate simulator. The journal makes a lost reply reconcilable; attempts retain history.
7. QC and clearance complete the demonstration. Metrics and sanitized logs help explain
   failures. The deployment is single-instance, with no high-availability promise.

## Concepts to know deeply

- Java collections, value equality, exceptions, constructor injection and test doubles.
- Spring request mapping, Bean Validation, security filters and transaction proxy boundaries.
- SQL joins, foreign keys, unique/partial indexes, row locks and the chosen isolation behavior.
- HTTP status semantics; retry safety; authentication, authorization, CSRF and input validation.
- At-least-once delivery, stable identities, idempotent effects and why “exactly once” is too broad.
- Immutable business snapshots and side effects that cannot be rolled back by a database transaction.

Know infrastructure at the level used here: one process/network/data path, one policy,
one migration, one backup and one release. Defer Kubernetes internals, distributed consensus,
Kafka tuning and broad cloud certification material unless the interview role requires them.

## Review record

For each exercise record: date, starting commit, task, expected result, commands/tests,
actual result, the diff Harsha made, explanation in his words and one remaining question.
Reviewer prompts should include a small variation not shown in the guide. Documentation
and API references are allowed; Harsha leads the reasoning and implementation.

| Gate | Status |
|---|---|
| HTTP/SQL/transaction explanation and independent change | Pending |
| Identity and file ownership exercise | Pending |
| Payment duplicate/error exercise | Pending |
| Concurrency/uncertainty debugging exercise | Pending |
| Deployment and isolated recovery exercise | Pending |
| Five-minute demo and follow-up mock interview | Pending |

## Honest discussion of assistance

A useful answer explains the process rather than hiding it: substantial implementation
assistance was used, changes were checked against explicit invariants, failures were
reproduced with tests, and personal ownership is demonstrated by the modifications and
debugging Harsha can perform now. Describe only reviews/exercises actually completed.
Do not claim independent mastery of a module after reading this document once.

## Difficult follow-up questions

1. If the payment provider returns success and your database is down, what happens next?
2. Which unique constraint stops two operators reserving one printer, and how is its error surfaced?
3. If a lease expires while a worker is still running, why is its eventual result not authoritative?
4. Can a signed upload overwrite a file after approval? Which exact version is downloaded?
5. Can you revoke a signed download that was already issued? What can the gateway revoke?
6. Can an inconsistent second payment block production today? Show the actual release check.
7. Why does returning HTTP 200 from the website prove neither the image SHA nor DB health?
8. If V2 migrates the database, why is redeploying the V1 image unsafe?
9. What does the restore exercise prove, and what data did its sample omit?
10. What evidence would make you extract a service or introduce a broker?
