# Roadmap, experiments and evidence

Status: draft · Last reviewed: 2026-09-30

## 1. MVP boundary

### OneCreations (existing site, repositioned as the parent)

- Real project photography across the existing pillars.
- Home, pillar indexes, detail pages, tutorials, CSE projects, contact form.
- One3D endorsement: header link, home section, footer, and "Create yours" links on printed pieces.
- A small journal can come later. See [brand and websites §4](brand-and-websites.md#4-onecreations-sitemap).

### One3D MVP

- **Products and orders:**
  - one custom-product offer;
  - reviewed model upload;
  - a simple business inquiry;
  - manual quote creation;
  - versioned approval.
- **Customer side:**
  - private assets;
  - payment integration;
  - a customer order timeline.
- **Operations:**
  - an operator work queue;
  - parts, jobs and attempt records;
  - manual slicing and dispatch;
  - QC and shipping records.
- **Measurement:**
  - labour and cost capture, with filament costed from the Numakers rate card;
  - internal scrap tracking by filament type.

### Technical proof inside the MVP

- A duplicate payment callback is harmless.
- An approved model cannot silently change.
- Two operators cannot reserve the same printer.
- A failed attempt keeps its resource cost.
- Customer files are private.
- Backups can be restored.
- A bridge simulator demonstrates uncertain-start reconciliation.

### Postponed

- **Automation:** instant quoting for arbitrary geometry, fully automatic likeness approval, autonomous physical retries, automatic bed ejection.
- **Customer features:** live customer video, a native mobile app, public credits before the ledger and economics work.
- **Recycling and claims:** household-waste filament, EPR certificate trading, carbon-offset claims.
- **Business models:** marketplace, franchising, general-purpose farm SaaS.
- **Infrastructure:** Kubernetes or a microservice fleet, and printers bought mainly for flagship status.

## 2. Twelve-week roadmap

This assumes 25–30 focused hours a week. Production and customer work count toward those hours.

| Week | Business / manufacturing | Software | Evidence |
|---|---|---|---|
| 1 | Audit the X1C, costs, space and skills. Define one product. Record the Numakers rate card. | Domain model and measurement definitions | Baseline assumptions |
| 2 | Produce representative samples and compare finishing methods | OneCreations umbrella repositioning (One3D link, section, copy) | Photographed physical samples |
| 3 | Interview prospects and seek paid commitments | One3D intake and private uploads | Qualified interest and objections |
| 4 | Fulfil the first narrowly scoped orders manually | Quotes, orders, operator queue | Timed labour and cost records |
| 5 | Refine the spec and revision policy | Payment callbacks, approval versions | Duplicate-payment test |
| 6 | Establish part QC and final QC | Parts, jobs, attempts, inspections | A traceable completed order |
| 7 | Document the X1C integration capability (LAN / Developer Mode) | Bridge simulator, journal, IoT connection | Duplicate-command test |
| 8 | Test real monitoring and supervised dispatch | X1C adapter and reconciliation | Firmware/capability evidence |
| 9 | Identify the actual bottleneck | Queue policy, alerts, cost dashboard | Before/after workflow measurement |
| 10 | Repeat a small batch | Failure tests, permissions, restore drill | Reproducible recovery evidence |
| 11 | Get recycler specs and quotes | Internal scrap ledger, synthetic credit tests | Material route and budget model |
| 12 | Review paid outcomes and the next investment | Deploy, document, record the demo | Go/stop decision per product |

Public PrintBack doesn't need to launch in these twelve weeks.

### Over the year

| Stage | Expansion condition | What to add |
|---|---|---|
| First paying customers | Product accepted at a viable price | Better intake, approvals, QC |
| Small farm | Printing is a measured bottleneck ([purchase gate](business-plan.md#printer-purchase-gate)) | Additional compatible machines, scheduling |
| Recycling pilot | Buyer, storage, receipts and funded rewards exist | One collection site, audited ledger |
| B2B production | Approved sample and viable batch economics | Saved profiles, repeat orders, invoices/POs |
| Larger operation | Repeat demand, enough people and cash | More capacity, tooling comparisons, suppliers |
| External software | Other farms commit to paying | Multi-tenant controls, support, installers |

Expansion is conditional, not scheduled by the calendar.

## 3. Experiments and stop/go rules

These are proposed decision rules, not industry benchmarks.

| Question | Experiment | Decision |
|---|---|---|
| Will people buy? | Real samples at real prices, request deposits | Count paid commitments, not compliments |
| Is the likeness acceptable? | Compare references, model and finished piece with customers | Drop styles with persistent dissatisfaction |
| Can the ₹2k and ₹5k tiers work? | Time 10 paid orders, including messages and rework | Reprice or reduce scope if labour destroys the margin |
| Does resin help? | Same design and finish via FDM and an outsourced resin supplier | Compare accepted quality and total cost |
| Does filament choice matter? | Same design in Numakers PLA+, Matte and a special | Keep the cheapest one that passes finish QC. The difference is about ₹100 per order at most. |
| Can production repeat? | Small batch from an approved master | Check variation, finishing time, breakage |
| Do credits cause purchases? | Eligible cohorts or staggered offers | Measure incremental contribution |
| Will people return material? | One site, a few scheduled collections | Accepted kg, participation, cost/kg |
| Does the material have value? | Real buyer quotes and pickup receipts | No positive default reward without a route |
| Is reprocessed material usable? | Several batches, virgin control, declared blends | Qualify a specific product, not "recycled plastic" |
| Does scheduling help? | Heuristic vs manual baseline on the same workload | Keep it only if lead time or effort improves |
| Buy another printer? | Identify lost profitable demand due to machine capacity | Buy only when downstream capacity exists |

If custom-figurine economics fail, shift to repeated collectibles, simpler personalisation, useful parts or B2B manufacturing. If consumer rewards fail, PrintBack can become an organisation-funded collection and education program.

## 4. AWS certification mapping

AWS says DVA-C03 registration opens 27 October 2026, and the certification page lists **1 December 2026 as the last day for DVA-C02**. Confirm the booking and study against the guide for the exam actually taken.

| DVA-C02 task | One3D application |
|---|---|
| 1.1 Develop on AWS | Sync commands, async work, SDK integration, event patterns, resilient AI/payment adapters, tests |
| 1.2 Lambda | Memory, timeouts, concurrency, triggers, destinations, error handling, performance |
| 1.3 Data stores | Access-pattern design, indexes, consistency, serialisation, backups, lifecycle, caching |
| 2.1 AuthN/AuthZ | Cognito, JWT validation, operator roles, ownership checks, assumed roles, CI OIDC |
| 2.2 Encryption | TLS, S3/DynamoDB encryption, device certificates, KMS key policies |
| 2.3 Sensitive data | Private photos, Secrets Manager, redacted logs, restricted exports |
| 3.1 Deployment artifacts | Locked dependencies, Lambda packages, container images, env config |
| 3.2 Dev testing | SAM/staging tests, provider simulators, duplicate and reordered events |
| 3.3 Deployment testing | Isolated environments, versioned fixtures, aliases, IaC checks |
| 3.4 CI/CD | Build/test/deploy pipeline, promotion, canary/blue-green, rollback |
| 4.1 Root cause | Failed uploads, dispatch, callbacks, deployments |
| 4.2 Instrumentation | Logs, metrics, traces, health checks, alarms, correlation |
| 4.3 Optimisation | Concurrency, profiling, caching, cost, sizing, bottlenecks |

Isolated labs, kept out of production:

- VPC access from Lambda;
- cross-account KMS;
- OpenSearch compared with simpler search;
- CodeBuild/CodePipeline/CodeDeploy;
- AppConfig rollout;
- cache invalidation;
- alternative deployment strategies.

These live in `labs/certification/`.

## 5. Five-minute demo

| Time | Segment |
|---|---|
| 0:00–0:40 | **The product.** A real finished object and its order record. |
| 0:40–1:20 | **Approval and traceability.** The approved revision, parts list, manufacturing profile and quote, with filament costed from the rate card. |
| 1:20–2:10 | **Dispatch.** Assign a simulated or real supervised job. Show the immutable command ID and durable edge acknowledgement. |
| 2:10–3:00 | **Failure.** Deliver the command twice: no second start. Disconnect the bridge mid-print: ownership is held because the physical state is uncertain. |
| 3:00–3:40 | **Recovery.** Reconnect and reconcile. A confirmed failed attempt becomes an authorised reprint, and the original material and time are kept. |
| 3:40–4:20 | **Operations.** Real labour, first-pass yield, queue time and contribution for a measured period. |
| 4:20–5:00 | **Material history.** Intake, credit entry, transfer, recovery receipt. Mark demo data as synthetic if PrintBack isn't live. |

The sustainability sequence is: measured intake → accepted/rejected weights → credit transaction → material movement → partner receipt → qualified output → product incorporation → dashboard update. The dashboard changes only as supporting evidence arrives.

## 6. Resume evidence (placeholders until measured)

- Built an AWS manufacturing application connecting [N] printers and tracking [N] physical production attempts.
- Implemented durable edge commands and reconciliation, verified through [documented failure scenarios].
- Reduced operator scheduling time from [baseline] to [result] across [sample size] jobs.
- Tracked [kg] of material through intake, inventory, processing and verified use.
- Measured infrastructure cost of [amount] per accepted job under [documented workload].

Publish the measurement window, definitions, code version and sample size. Keep simulated concurrency separate from manufactured output.

In interviews, be ready to explain:

- why "exactly once" is hard around physical machines;
- how double spending and duplicate production were prevented;
- why each access pattern was chosen;
- how approval versions constrain manufacturing;
- how failures affect real costs;
- how collection was distinguished from verified recovery.
