# One3D V1 release and portfolio review

Review started 2026-10-14; finalized 2026-10-15. Base: `940a6070e2bc2812c4cbba2687abc33d77f6a250`.
Status: **release preparation; no accepted release, live cutover or private-access claim**.

Read the [verification record](evidence/2026-10-14-release-preparation.md),
[release procedure](release-runbook.md), [private access design](private-demo.md)
and [interview exercises](interview-defense.md) alongside this assessment.

## 1. Executive assessment

One3D is a strong, substantial fresher backend capstone. Its strongest evidence is
how it handles ownership, duplicate messages, immutable customer files and uncertain
execution. These are more useful interview topics than the length of the tool list.

Keep the modular monolith. Java, PostgreSQL and a small frontend cover the application;
the separate simulator has a concrete purpose: it can keep running when the backend
restarts. Splitting the application into services would add deployment and consistency
work without solving a measured problem.

The project already exceeds the minimum scope needed for an entry-level portfolio.
The next investment should be reliable release operation and personal understanding.
Neither a working deployment nor this review establishes Harsha's ability to explain
and change the code. The ownership gates remain pending.

**Do not tag the supplied candidate yet.** CI passed, but the purported successful
deployment job actually skipped deployment because EC2 was stopped. A later manual
run failed while the remote command still reported `InProgress`. Runtime identity,
private access, acceptance and recovery of the final release still need evidence.

## 2. Current implementation audit

Evidence levels used here: **source** means inspected implementation; **recorded**
means existing checkpoint evidence; **remote CI** means a run independently read from
GitHub; **reported** means the owner's account; **pending** means not verified.

| Area | Evidence and conclusion |
|---|---|
| Catalog and custom workflow | Source: orders, requests and quotes modules implement the bounded workflows; the UI exposes customer/operator paths. |
| Immutable files | Source: `FileService.finalizeUpload` and `ArtifactVerifier` bind verification to an S3 version and checksum. `FilesIT` covers authorization, invalid files and stale workers. This is not malware scanning or proof of manufacturability. |
| Quotes | Source: `QuoteService.accept`, `RequestService.lockForAcceptance` and PostgreSQL constraints protect acceptance. `QuotesIT` exercises expiry and races. |
| Payments | Source: `PaymentRecorder.record`, `RazorpayWebhookController` and V8 migrations protect signed test payment processing and deduplication. `RazorpayWebhookIT` checks duplicates and transaction rollback. |
| Release policy | Source limitation: `ProductionService.release` calls `OrderService.requireReleasable`, which checks `CONFIRMED`. It does not query storage availability, material readiness or unresolved payment-review facts. Human release is not an automated safety certification. |
| Dispatch and reconciliation | Source: PostgreSQL work claims, stable command IDs, independent SQLite simulator journal and bed-cycle checks. The review found malformed-response and oversized-diagnostic gaps; this preparation includes focused fixes and regressions. |
| Authentication | Source: `SecurityConfig` uses OIDC login and a server-side session; roles and resource ownership are enforced in the backend. This is not a browser JWT bearer API. |
| Delivery | Remote CI: [candidate CI](https://github.com/harsha135135/One3D/actions/runs/38418619854) passed simulator, backend, frontend and image jobs. Its deploy job skipped. [Manual promotion](https://github.com/harsha135135/One3D/actions/runs/38419467403) failed; actual remote outcome is pending. |
| Tests | Recorded CP9: **242 backend tests including 187 integration tests**, plus 14 simulator and 26 mocked-API browser tests. These are not 242 + 187 backend tests. Fresh preparation results are recorded separately. |
| Recovery | Recorded: 15 table counts, one catalog-order chain and one exact artifact version/checksum matched. The CP9 restore log contains zero custom requests, revisions and quotes: it does not prove recovery of a custom-order chain. |
| AWS experience | Recorded checkpoint deployment, fault investigation and instance replacement; reported owner demonstrations of catalog/custom flows. Current resource state and deployed digests are pending because the AWS connector failed before executing an API. |
| Private demo and public brand site | Private access is a proposed design. The Coming Soon files are prepared locally. Neither has been cut over. |
| Acceptance | Checkpoint 1 owner review recorded; the remaining owner reviews, accepted V1 and actual résumé update remain pending. |

The baseline also records an operator queue stale-reload issue in CP9. Rehearse it
before an interview; a manual refresh is an honest workaround, not evidence of a fix.

## 3. Architecture critique

### Decisions worth defending

- **Snapshot accepted scope:** later catalog or model edits must not rewrite an order.
- **Separate job and attempt:** retries preserve history and cannot pretend the first
  execution never happened. QC and bed clearance record different facts.
- **Represent uncertainty explicitly:** a network timeout cannot prove a printer did
  not start. Reconcile the original command rather than minting a new execution.
- **Use PostgreSQL invariants:** locks and unique indexes arbitrate concurrent requests
  across application processes; a Java `synchronized` block would not do that.
- **Keep external calls behind small interfaces:** storage, payments and simulator
  clients can be tested without creating an internal network of microservices.
- **Keep files private and versioned:** signed access is issued after authorization;
  a quote points at reviewed bytes rather than whichever object is currently latest.

### Costs and limits

The stack is justified as a learning demonstration, but Cognito, payment integration,
Terraform, TLS, SSM and monitoring make operations significantly larger than a CRUD
project. Freeze that investment instead of adding Kafka, RabbitMQ, Kubernetes,
Jenkins or a second cloud. Prometheus/Grafana are sufficient; no tracing platform is
needed unless a real debugging problem warrants it.

Single-instance Compose means planned downtime and one failure domain. PostgreSQL
queues avoid another broker but require leases, retries and stale-result protection.
The simulator tests an explicit software protocol, not the durability guarantees of
a physical printer. A future adapter cannot inherit those guarantees by assumption.

Two concrete error paths deserved fixes: invalid simulator JSON was interpreted as a
definite rejection, and an oversized failure message could repeatedly violate the
database column limit. The new behavior must retain uncertain ownership and save a
bounded diagnostic. This changes application code, so a **new tested candidate** is
preferable to calling the unchanged `940a607` release corrected.

The documented release policy also promises no unresolved hold and available artifacts.
The implementation does not enforce that entire policy. Before acceptance, explicitly
resolve this scope mismatch: implement a bounded hold/availability check with tests, or
approve a narrower operator-check demonstration and update the scope. This review does
not silently waive that gate or add a new payment-resolution subsystem.

## 4. V1.0.0 release-freeze plan

Use the [ordered runbook](release-runbook.md). In brief:

1. Preserve the current candidate, inspect its CI and running images, and resolve the
   outstanding SSM command before any promotion. A success badge is not runtime evidence.
2. Review and merge the bounded fixes only after approval. If application fixes are
   included, identify the new exact commit, run CI and deploy it explicitly for acceptance.
3. Apply the reviewed permission reduction and retention changes with approval. Removing
   automatic deployment in YAML is insufficient while the CI role can run arbitrary SSM commands.
4. Capture immutable image digests, versioned deployment bundle, reviewed operations
   revision, migration history/checksums, configuration references and recovery artifacts.
5. Rehearse redeployment and isolated restoration; preserve the simulator journal and
   exact S3 artifact versions, not only a database dump. Do not downgrade a migrated live DB.
6. Complete private-access tests and Harsha's explanation/change/debug review. Record
   acceptance, then request approval for the exact annotated `v1.0.0` tag and release.

A Git tag names source, not data or an entire environment. Protect it against update
and deletion using a GitHub tag ruleset if available for this repository. Preserve
release artifacts independently of GitHub Actions log retention. Pinned dependencies
still need occasional deliberate security maintenance; do not promise a forever-safe binary.

## 5. Private demo security architecture

Recommended: **Cloudflare Access plus an outbound Tunnel**, with Nginx reachable only
through the tunnel and administrative SSM. It gives selected recruiters a browser
login without installing a VPN. Keep Cognito for the application's customer/operator
roles. Protect the whole hostname, including pages, APIs and login callbacks.

Close public ingress on the instance, including IPv6 and alternate security groups;
an Access login at the hostname alone leaves an origin-IP bypass. Cloudflare documents
the outbound-only model and blocking inbound traffic in its
[Tunnel firewall guide](https://developers.cloudflare.com/cloudflare-one/networks/connectors/cloudflare-tunnel/configure-tunnels/tunnel-with-firewall/).

Tailscale is reasonable for Harsha's laptop and screen sharing, but invited reviewers
need tailnet/client setup and Razorpay cannot reach a private tailnet webhook. Nginx
Basic Auth is smaller but has poorer invitation/revocation ergonomics and another
credential to manage. The full callback, webhook, signed-URL and revocation design is
in [private-demo.md](private-demo.md). This design has not been deployed or penetration tested.

## 6. Public Coming Soon deployment architecture

The public site is static files in `public-coming-soon/dist`. It has no application
API, payment credentials, customer uploads or dependency on EC2. Hosting it on a static
platform keeps the brand page online while the demo is stopped.

Use **Cloudflare Pages if the Access/Tunnel decision also moves authoritative DNS to
Cloudflare**. Hostinger remains the registrar. Pages requires Cloudflare nameservers
for an apex custom domain; a subdomain can instead use external DNS. See
[Pages custom domains](https://developers.cloudflare.com/pages/configuration/custom-domains/).

If retaining Hostinger DNS is the priority, Netlify is a straightforward static-hosting
alternative: its ordinary external-DNS apex fallback is `A @ 75.2.60.5`, and `www`
points to the assigned site hostname. Confirm the actual project's settings before
editing DNS; no site hostname has been provisioned. See
[Netlify external DNS](https://docs.netlify.com/manage/domains/configure-domains/configure-external-dns/).

The observed Hostinger parking apex conflicts with either new host. Preserve all MX,
TXT, CAA, validation and unrelated subdomain records during a nameserver migration.
Do not point the public apex at the EC2 address. Detailed deployment/rollback steps
and known DNS values are in the public site's README.

Free tiers may fit this small static page; confirm the current account limits before
activation. No extra EC2, load balancer, NAT gateway or paid hosting plan is required.
Stopping EC2 saves compute time but retained EBS, ECR, S3 and public IPv4 can still cost
money. AWS charges for both in-use and idle public IPv4; see
[VPC pricing](https://aws.amazon.com/vpc/pricing/). Keep budgets and retained-image size
visible rather than treating AWS credits as a zero-cost architecture.

## 7. V2 development strategy

After accepted V1 and the résumé update, develop one approved increment on
`feat/v2-<capability>` branches. Keep one CI pipeline. Main can accept reviewed V2 work
without deploying the recruiter environment; promotion is an owner operation with an
explicit manifest. Do not create a long-lived second CI system or merge V2 back into
an immutable V1 tag.

V2 development uses a separate local Compose project, volumes, database and synthetic
identities. It must have no demo AWS credentials by default. A future hosted V2 needs
separate state, data, buckets/prefix permissions, secrets, user pool/client and release
configuration. A branch name alone is not isolation. Never run V2 Flyway migrations
against the V1 database or share the simulator journal.

If time remains, the first real-printer increment should be **read-only telemetry**
behind a narrow adapter, with saved fixtures and explicit permission for any hardware
access. Do not begin with remote start commands. Keep payments, uploads and the
modular monolith. Consider service extraction only after measured independent scaling,
failure isolation or team ownership needs outweigh the operational cost. A printer
count alone does not justify microservices.

Postpone multi-printer scheduling, live money, automatic slicing, multi-part orders,
inventory, shipping and optional infrastructure labs until after placements or a
separately approved business increment.

## 8. Interview defense curriculum

Study in this order; the [code map and exercises](interview-defense.md) turn each into
an explanation, change and debugging task.

1. Java, exceptions, dependency injection, DTOs, validation and HTTP request flow.
2. SQL schema, JPA behavior, Flyway, snapshots, transaction boundaries and constraints.
3. Concurrent acceptance/assignment; why database locks and indexes both matter.
4. Authentication versus authorization; OIDC code flow, session cookies, CSRF and ownership.
5. Payment trust, raw-body signatures, duplicate delivery and transaction rollback.
6. Durable work, leases, command identity, uncertainty, reconciliation and bed clearance.
7. Docker networks/volumes and the actual browser → Nginx → backend → database path.
8. AWS IAM/network/storage boundaries, infrastructure state, release pins and data recovery.
9. Logs and metrics: reproduce one failure, find it, explain the diagnosis and limits.

Understand application invariants deeply. Explain AWS products and monitoring at the
level of the deployed paths and permissions; memorizing every service API is unnecessary.

## 9. Hands-on ownership exercises

Harsha should personally perform at least one task in each critical area, with a short
review record. Reading an explanation or approving generated code is insufficient.

- Change a quote-expiry rule using a controlled clock and test the exact boundary.
- Reproduce competing printer assignments and point to the transaction/index that wins.
- Send a duplicate signed payment event and explain why no second business effect appears.
- Attempt another customer's order/file access and trace the rejected request.
- Break a simulator acknowledgement, retain ownership, then reconcile from its journal.
- Restore synthetic catalog **and custom** order chains in isolation and verify exact files.
- Explain a Docker network or configuration failure, then correct it without changing business rules.
- Walk through a proposed release manifest and reject a wrong digest or wrong database history.

Explain assistance honestly if asked: describe which parts were generated or suggested,
what you reviewed and tested, and what you can now change yourself. Avoid saying every
line was independently authored. Technical ownership is demonstrated through reasoning
and modifications, not an attribution footer or memorized architecture speech.

## 10. Risk-ranked action plan

| Priority | Risk | Action |
|---|---|---|
| Must, 1 | Unknown deployed SHA/unfinished remote command | Read current SSM result, container digests and health before asserting a freeze. |
| Must, 2 | Future CI can overwrite demo; retention can erase releases | Remove CI SSM permission, manual explicit pins, retain release images/bundles. Apply only after approval. |
| Must, 3 | Public origin bypass / privacy untested | Configure Access/Tunnel and close ingress; test anonymous, origin, callback, revocation and webhook cases. |
| Must, 4 | Failure/recovery gaps | Merge tested protocol and recovery-script fixes; rehearse final rollback/restore; resolve release-policy mismatch. |
| Must, 5 | Personal ownership incomplete | Complete explanation/change/debug gates before accepted release and associated résumé claims. |
| Should | Fragile interview demo | Rehearse on a fresh browser, refresh queue if required, keep a verified recording and local fallback. |
| Should | Public links imply live commerce | Use Coming Soon labels and public apex links in OneCreations. |
| Optional | V2 polish and tooling labs | Defer until the above and placement preparation are on track. |

## 11. Résumé positioning

Use two or three bullets after the relevant evidence and personal review. Suggested
wording, conditional on completion:

- Built a Java 21/Spring Boot and PostgreSQL order workflow with versioned model
  approvals, manual quotes and customer/operator access controls.
- Implemented Razorpay sandbox payment verification and durable simulated printer
  attempts; tested duplicate events, concurrent assignments and lost acknowledgements.
- Deployed the demo on AWS using Docker Compose, private S3, Cognito and GitHub Actions;
  investigated an injected failure and verified restoration into an isolated database.

Keep “sandbox payments” and “simulated printer” in the description. Do not claim
commercial customers, production farm automation, high availability, zero data loss,
unmeasured performance gains or a fully reproduced disaster-recovery environment.
Do not describe a configured dashboard as proof you diagnosed an incident.

## 12. Final verdict

**Engineering scope: more than sufficient for a strong fresher project. Release and
interview readiness: conditional, not complete.** Finish the small reliability fixes,
verify the actual deployed candidate, protect and preserve it, then spend the majority
of effort on demonstrating understanding. More features will not compensate for weak
SQL, Java, concurrency or debugging explanations.

Suggested weekly preparation allocation (a starting point, not a hiring guarantee):
40% DSA/problem solving, 20% core CS/SQL, 15% Java/Spring fundamentals, 20% One3D
exercises and mock interviews, 5% applications/résumé review. Allocate **0% to V2**
until acceptance and interview defense are solid. If those are strong, move at most
5 percentage points from project rehearsal to one bounded V2 experiment. Adjust after
actual mock interview results rather than adding tools to match job-description keywords.
