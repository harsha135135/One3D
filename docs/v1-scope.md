# One3D V1: a backend project for a fresher portfolio

Status: agreed direction; implementation not started · Last reviewed: 2026-10-07

## 1. Purpose and scope authority

**V1 exists to help Harsha demonstrate that he can build, explain, test, deploy and debug a modest backend application.** It is a hiring portfolio project based on One3D's domain. V2 and later can add the complexity needed to operate the business.

The goal is credible evidence for entry-level backend roles, not a guarantee of employment. A working application Harsha understands matters more than the number of technologies named in its README. The earlier business plan, farm architecture and tool wish list must not silently become V1 requirements.

This file is the V1 scope authority. [ARCHITECTURE.md](../ARCHITECTURE.md) describes the design; [roadmap.md](roadmap.md) defines checkpoints; [IMPLEMENTED.md](../IMPLEMENTED.md) records actual capabilities; [resume-evidence.md](resume-evidence.md) governs résumé claims. New user decisions can change the scope, but update this file before implementing the change.

## 2. What the demonstration does

A customer chooses a known product or submits one model for a manual quote. An operator approves the manufacturing definition. The customer pays through a payment provider's test mode. The operator releases the order and assigns it to a simulated printer. The application records attempts, handles a failed or uncertain start, waits for bed clearance and a quality check, and marks the order ready for handoff.

The customer sees a simple progress timeline. The operator sees which orders and tasks need attention.

**No real printer is controlled and no real customer money is collected in V1.** Demonstration customers, production results and fulfilment records are synthetic and labelled accordingly.

### Fixed limits

| Area | V1 boundary |
|---|---|
| Catalog | One seeded product with one configuration; no catalog editor, cart or promotions |
| Custom intake | One STL per submission revision, maximum 20 MB; short requirements form and manual review |
| Orders | One line, quantity one, one currency; money stored in integer minor units |
| Manufacturing | One approved recipe, one plate job and one required part per order; no assembly or multi-part bill of materials |
| Printer | One durable, independently restartable simulator using simple HTTP and a local journal; jobs are serialized |
| Attempts | Preserve failed attempts; an operator can authorize another attempt for the same job |
| Quality | Whole-part pass/fail inspection; failed output does not satisfy the order |
| Fulfilment | Manual “ready for handoff” and “handed over”; no carrier integration or partial shipment |
| People | Customer and operator roles; at least two customer identities for ownership testing |
| Environments | Local development and one disposable hosted demo; no production availability promise |
| Frontend | Functional responsive customer/operator pages; no advanced 3D viewer or elaborate effects |

The 20 MB limit deliberately narrows V1. The earlier requirement for 200 MB uploads remains a V2 business target. Larger files, more formats and production quantities need an explicitly approved later scope.

## 3. Required application capabilities

### Access

- One authentication approach as documented in the architecture, with backend-enforced role and record-ownership checks.
- Customers see only their submissions, quotes, files and orders.
- Only operators review, quote, release production, reconcile attempts and record QC/handoff.
- Authorized file access uses short-lived signed links; a hidden operator URL is not access control.

### Catalog and custom work

- Catalog orders snapshot product configuration, price and approved recipe revision.
- An upload creates a request, not an order or print command.
- Files and requirements form an immutable submission revision. Replacement files or changed requirements require a new revision and review.
- The operator can request changes, reject the request or approve it for a quote.
- A quote identifies the reviewed revision, recipe, price and expiry. Acceptance freezes scope and creates at most one order per quote revision.
- Use a checkout window no longer than the accepted payment window. Late or inconsistent payment outcomes enter manual review; they must not silently release production.

### Files

- Direct upload to private storage; the Spring API handles authorization and metadata.
- Random server-assigned keys, size limits and conservative type checks.
- Finalization records an exact object version and verified checksum; approval never follows a mutable “latest” object key.
- Download-only operator review; no automatic geometry repair, rendering or slicing service.
- No customer G-code, automatic execution of uploads, signed URLs in logs or automatic portfolio publication.

These checks demonstrate a bounded upload workflow. They do not establish that an STL is safe for every parser or suitable for every manufacturing use. Treat uploaded models as untrusted data.

### Payment in test mode

- One provider, server-created payment identity/amount and signature verification using the documented payload requirements.
- Deduplicate provider events and payment identities with database constraints.
- Verify amount, currency, provider order and successful payment state. A checkout success screen is not sufficient evidence.
- Repeated success delivery cannot create another order or production release.
- An unexpected second payment or unresolved result is visible for manual investigation.
- Live payments, refunds, deposits, accounting and tax engines are deferred.

### Production and simulator

- Payment establishes commercial eligibility; an operator separately releases approved scope with available artifacts and no hold.
- A job is planned work; an attempt is one execution. A retry creates a new attempt.
- Reserve printer/job and create the attempt atomically; prevent competing active ownership at the database level.
- A command has a stable identity. The simulator journals it before execution and handles duplicates without starting again.
- The simulator can complete, fail, drop a start acknowledgement and restart independently of the backend.
- An uncertain start holds ownership for reconciliation. A timeout does not authorize reassignment or automatic retry.
- Completion or failure leaves the bed unavailable until operator clearance. A simulated abort follows the same rule; a customer cancellation workflow and printer cancellation API are outside V1.
- QC and clearance are separate facts. Customer progress comes from saved business facts, not an editable percentage.

Simulator tests establish software behavior under documented scenarios. They do not prove real printer reliability or physical safety.

## 4. Stack and bounded hands-on requirements

Introduce tools after the preceding application slice works. Do not scaffold the whole toolchain before the first order runs.

| Area | Required V1 exercise | Stop here |
|---|---|---|
| Java 21, Spring Boot, Maven | REST, validation, transactions, authorization and tests | One modular monolith; no microservices |
| PostgreSQL, JPA, Flyway | Relationships, queries, constraints, indexes and fresh-database migrations | One operational database; no Redis or parallel DynamoDB |
| Next.js, React, TypeScript | Small customer/operator interfaces calling the API | No duplicate business rules in a second backend |
| Docker Compose | Start app, database and simulator; recreate containers without losing persisted records | No production orchestration platform |
| AWS | One EC2 demo, private S3 artifacts and narrowly scoped access/identity | No EKS, IoT Core, serverless rewrite or multi-region deployment |
| Nginx | HTTPS and frontend/API routing, with internal ports private | One reverse proxy |
| GitHub Actions | Checks, versioned image build and controlled promotion to the demo | One CI/CD system |
| Terraform | Plan and recreate the declared disposable demo resources once | One environment; no platform-module library |
| Prometheus | Small set of application/runtime metrics | No monitoring cluster or per-customer labels |
| Grafana | One dashboard showing request rate/errors/latency and pending work; diagnose one injected failure | No dashboard collection or extensive alerting system |
| Recovery and cost | Restore a backup to a separate test database; record demo resource cost and cleanup | No business uptime or disaster-recovery promise |

GitHub Actions is the default. Choosing Jenkins instead is an explicit substitution, not a requirement to complete both. Keep secrets out of Git and logs; use scoped, short-lived access where supported. Monitoring endpoints and dashboards stay private.

Use only the AWS identity setup required by the chosen authentication design. The demo can have a single failure domain if that limitation and recovery procedure are explicit; do not call it a resilient production deployment.

### Optional labs do not block V1

| Tool | One bounded later exercise |
|---|---|
| Ansible | Configure a disposable VM; explain why a second run makes no unnecessary changes |
| Jenkins | Reproduce the CI workflow in a separate lab, or explicitly substitute it for GitHub Actions |
| RabbitMQ | Notification worker with acknowledgement, retry, duplicate handling and failed-message recovery |
| Kafka | Replay synthetic printer events to rebuild a summary |
| Kubernetes | Run locally, replace a failed pod and perform/reverse a rolling update |

Each chosen lab needs setup, cleanup, limitations and evidence. It must not become a V1 dependency. Finish and present V1 before expanding this list. Describe a lab as a lab on the résumé until the tool actually serves the application.

## 5. Code quality expected at completion

“Industry standard” here means practices Harsha can demonstrate and explain, not a certification or enterprise-scale complexity.

- Organize by business feature with clear HTTP, application/service, domain and persistence responsibilities; follow the architecture's proposed tree.
- Controllers handle HTTP; application services own use cases and transaction boundaries. Do not expose persistence entities as API contracts.
- Validate boundary input and enforce state-transition rules in the backend.
- Use consistent errors, meaningful HTTP responses and pagination for growing lists.
- Use migrations, foreign keys, constraints and explicit ownership relationships.
- Keep remote payment/storage calls outside long database transactions; preserve stable identities across retries.
- Use small adapters for actual external boundaries. Avoid an interface for every class or a generic workflow engine.
- Keep configuration/secrets outside code; commit safe examples and reproducible dependency definitions.
- Test important rules and adverse cases. Verify database behavior against PostgreSQL rather than assuming an in-memory substitute is equivalent.
- Provide structured logs, health checks and useful correlation IDs without leaking customer data.
- Document setup, API usage, architecture, limitations, backup/restore and the demo.
- Keep CI green; fix relevant security/static-analysis findings or record a reasoned disposition.

No arbitrary coverage percentage, endpoint count, load number or repository size is a substitute for readiness.

## 6. V1 completion gate

All items below require evidence. This unchecked list is not a report of completed work.

- [ ] Catalog and custom-order journeys work end to end with labelled demo data.
- [ ] Authentication, customer isolation and operator permissions pass the recorded tests.
- [ ] File/quote revisions and accepted order scope remain immutable.
- [ ] Verified test payments advance orders once; forged/duplicate events do not release extra work.
- [ ] Concurrent reservations, duplicate commands, failed/uncertain attempts, restarts, bed clearance and QC pass defined scenarios.
- [ ] Harsha can run the application from a clean checkout with documented prerequisites.
- [ ] Hosted demo, CI, Terraform exercise and HTTPS routing have recorded evidence.
- [ ] Monitoring, database restore, resource cost and cleanup have recorded evidence.
- [ ] Code, setup, architecture, limitations and [IMPLEMENTED.md](../IMPLEMENTED.md) agree.
- [ ] Harsha passes the ownership checks and personally signs off on V1 completion.
- [ ] A stable V1 reference, demo and [résumé evidence](resume-evidence.md) are ready.

Then pause feature expansion, update the résumé with verified V1 claims, and begin applications/interview preparation. Optional labs and V2 must not delay this checkpoint. A job offer is not an acceptance criterion because project scope cannot guarantee one.

### Ownership checks

For every critical path, Harsha can:

1. Trace a request from UI through Java to SQL and saved records.
2. Explain the API, schema, validation and transaction boundary.
3. Change a rule, update its test and explain the result.
4. Diagnose a seeded failure with logs, metrics and the database.
5. Explain a simpler alternative and why this approach was chosen.
6. Distinguish simulated, locally tested, cloud-demonstrated and merely planned behavior.

At the final checkpoint, demonstrate quote acceptance, payment deduplication, reservation and uncertain-start recovery without an assistant supplying the explanation. Consulting documentation is normal; unexplained generated code is not ownership evidence.

## 7. Instructions for implementation sessions

1. Read this scope, the roadmap and IMPLEMENTED.md before choosing work.
2. Implement the next unfinished checkpoint as a small runnable slice.
3. Explain the change, verification and what Harsha should explain or modify.
4. Record evidence only after running a check. Never label a plan as implemented or a demo as a production result.
5. Put work outside this scope in the later backlog; historical architecture and business documents do not authorize it.
6. When gates pass, state that V1 is ready for Harsha's signoff and résumé checkpoint. Do not silently continue to V2.
7. Begin V2 or a new lab only after Harsha requests it. Give the next increment its own bounded scope and completion gate.

## 8. Explicitly outside V1

Real printer control; live paid manufacturing; 200 MB/multi-file/archive uploads; automatic model repair/slicing/quoting; AI likeness generation; multiple configurations/materials; multi-part assembly and quantity accounting; cross-order packing; spool inventory; automatic scheduling or bed handling; live video; customer cancellation/refunds/accounting; shipping integration; PrintBack/credits/recycling; multiple farms; SaaS tenancy; high availability; microservices; and the optional labs above.

The X1C and the broader business ambitions provide context, not prerequisites for the portfolio release.
