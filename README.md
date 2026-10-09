# One3D by OneCreations

**V1: a focused Java backend portfolio project, built to demonstrate fresher-level engineering skills.**

One3D is the planned custom-manufacturing business of [OneCreations](https://github.com/harsha135135/OneCreations). Its operating software is called **PrintFlow**. Checkpoint 1 of V1 (a catalog order API on Spring Boot and PostgreSQL) is implemented, tested locally and signed off by the owner. Checkpoint 2 (release, jobs, attempts, a simulated printer, bed clearance and QC) is implemented and tested locally; its owner review is pending. Later checkpoints, infrastructure and deployment are not implemented yet, and V1 is not complete.

Status: Checkpoint 1 complete (signed off 2026-10-09; schema explanation reference-assisted, to revisit); Checkpoint 2 implemented and tested locally, owner review pending · Scope updated: 2026-10-07

## What we are building first

V1 demonstrates a small order workflow: choose one catalog product or upload one STL, get a manual review and quote, approve the revision, make a sandbox payment, release a job to one simulated printer, and record quality inspection. Customers see their own orders; operators handle review and production.

The limits are deliberate: **one order line, quantity one, one part and one plate job per order**. Each custom revision contains one STL of at most 20 MB. Real printer control, live payments, multi-part manufacturing, farm optimisation, inventory and recycling are later work.

The hiring evidence comes from explaining and testing the workflow: authorization, SQL transactions, file ownership, payment verification, duplicate requests, failed jobs and recovery. No project guarantees hiring; V1 should provide credible examples for a résumé, code review and technical interview.

## Read these documents in order

| Document | Authority and purpose |
|---|---|
| [V1 scope](docs/v1-scope.md) | **Current scope and definition of done.** Contains the limits, checkpoints and ownership requirements. |
| [Implementation status](IMPLEMENTED.md) | What actually exists, what has been tested and where the evidence is. Planning is not implementation. |
| [V1 architecture](ARCHITECTURE.md) | How to structure and build the bounded application. |
| [Roadmap](docs/roadmap.md) | The sequence of checkpoints, followed by the résumé gate and later increments. |
| [Résumé evidence](docs/resume-evidence.md) | How to substantiate a claim and decide when it belongs on the résumé. |
| [Contributor instructions](AGENTS.md) | Rules for keeping implementation inside V1 and recording honest progress. |

The latest explicit owner instruction takes precedence. Otherwise, `docs/v1-scope.md` sets the current implementation boundary. Future plans and historical documents do not add V1 requirements.

## Intended stack

| Area | V1 choice |
|---|---|
| Backend | Java 21, Spring Boot, Spring Security; one application with feature modules |
| Data | PostgreSQL, Spring Data JPA and Flyway migrations |
| Frontend | Minimal Next.js / React / TypeScript customer and operator screens |
| Identity and files | Cognito identity integration; private S3 files with ownership checks |
| Payments and production | One payment provider in test mode; one independently restartable printer simulator |
| Local environment | Docker Compose |
| Deployment | One small AWS EC2 environment, Nginx HTTPS, scoped IAM |
| Delivery | GitHub Actions as the single primary CI pipeline; Terraform for that same environment |
| Monitoring | Prometheus and Grafana: a few useful metrics, one dashboard and a demonstrated failure diagnosis |

These are planned choices, not claims of hands-on experience. The [scope](docs/v1-scope.md) defines the minimum evidence for each required tool. **Ansible, Jenkins, RabbitMQ, Kafka and local Kubernetes are optional labs.** They must not become prerequisites for finishing V1. There is no required microservice split, message broker, Kubernetes cluster or multi-region deployment.

## Run it locally

These commands were run on 2026-10-07 and 2026-10-09; results are in the [Checkpoint 1](docs/evidence/2026-10-07-checkpoint-1.md) and [Checkpoint 2](docs/evidence/2026-10-09-checkpoint-2.md) evidence.

**Prerequisites:** JDK 21 (checked with 21.0.12; point `JAVA_HOME` at it if your default JDK differs) and a running Docker daemon. Maven is not required: use the included `./mvnw` wrapper.

```bash
# 1. Start the local PostgreSQL database (published on 127.0.0.1:5433 only)
docker compose up -d db

# 2. From backend/, run the unit tests (Surefire) and the PostgreSQL
#    integration tests (*IT, Failsafe + Testcontainers)
cd backend
./mvnw verify

# 3. Start the API on http://127.0.0.1:8080 (Ctrl+C to stop)
./mvnw spring-boot:run

# 3b. Or start it with the demo-only simulated payment, needed to
#     walk an order through production before real payments exist
./mvnw spring-boot:run -Dspring-boot.run.profiles=demo
```

The integration tests start their own disposable PostgreSQL container, so step 2 does not need step 1. Stop the local database with `docker compose stop`; its data stays in the `one3d-db` volume.

| Method and path | Purpose |
|---|---|
| `GET /api/catalog/configurations` | List orderable configurations (one seeded demo item) |
| `POST /api/orders` | Place a one-line order: `{"productConfigurationId": "…", "quantity": 1}` returns 201 and a `Location` header. `quantity` may be omitted or null (it defaults to 1); any other value, including a fractional number such as `1.5`, returns 400 |
| `GET /api/orders/{id}` | Read an order and its catalog snapshot |

Operator production endpoints (Checkpoint 2). `/api/ops` is a grouping, not access control:

| Method and path | Purpose |
|---|---|
| `POST /api/demo/orders/{id}/simulate-payment` | **Demo profile only.** Confirm an order without payment; the order records `confirmationSource: DEMO_SIMULATED` |
| `POST /api/ops/orders/{id}/release` | Create the order's production job (order must be `CONFIRMED`) |
| `POST /api/ops/jobs/{id}/attempts` | Start the next attempt: `{"printerId": "…"}`. Needs an idle printer with a cleared bed. At most 3 attempts per job, counting print failures and QC failures; a 4th returns 409 `ATTEMPT_LIMIT_REACHED` |
| `POST /api/ops/attempts/{id}/simulate-completion` / `simulate-failure` | Report the simulated printer's outcome; failure takes `{"reason": "…"}` |
| `POST /api/ops/attempts/{id}/qc` | Whole-part inspection: `{"result": "PASSED" or "FAILED", "notes": "…"}` |
| `POST /api/ops/printers/{id}/clear-bed` | Operator confirms the bed is empty; nothing clears it automatically |
| `POST /api/ops/orders/{id}/ready-for-handoff` | Only after the job's part passed QC |
| `GET /api/ops/jobs/{id}`, `GET /api/ops/printers` | Job with all attempts; printer ownership and bed state |

The seeded printer `0d3d0000-0000-4000-8000-000000000101` is simulated; no real printer is controlled. An invalid step returns 409 with a code such as `BED_NOT_CLEAR` or `JOB_NOT_ACCEPTED`.

```bash
curl -s -X POST http://127.0.0.1:8080/api/orders \
  -H "Content-Type: application/json" \
  -d '{"productConfigurationId":"0d3d0000-0000-4000-8000-000000000002","quantity":1}'
```

More requests, including invalid ones, are in [`backend/api.http`](backend/api.http). Errors are `application/problem+json` responses with a stable `code` (for example `VALIDATION_FAILED`, `CONFIGURATION_NOT_ORDERABLE` or `ORDER_NOT_FOUND`).

There is **no authentication** before Checkpoint 4. For that reason the API listens on 127.0.0.1 and the database port is published on localhost only. Do not expose either until identity (Checkpoint 4) and the proxy (Checkpoint 7) exist.

## V1 completion and the next step

V1 is complete only when the required workflow, failure tests, CI/deployment/restore checks and documentation work, and Harsha can explain, change and debug the critical code. The owner then accepts V1 and updates the résumé using recorded evidence. An agent cannot approve the owner's understanding on their behalf.

After that gate, agree one small V2 increment based on real business needs. Do not start implementing the full future architecture simply because V1 code builds.

Add run, test and demo commands for later checkpoints only after they have been verified. Do not present planned commands as runnable instructions.

## Websites and brand

| Address | Purpose |
|---|---|
| `onecreations.in` | Creative studio and public portfolio, maintained in its separate repository |
| `one3d.store` | Intended One3D customer application and V1 demonstration |
| `one3d.store/ops` | Operator screens in the same application, protected by backend authorization |

These are intended destinations, not a claim that One3D is deployed. One3D uses the Vantablack direction recorded in [brand and websites](docs/brand-and-websites.md). OneCreations uses Matte Black. The visual direction does not expand V1 into a full commercial storefront.

## Future business context

The business starts with one Bambu Lab X1C without AMS and a ₹3–4 lakh total first-year budget. Printer purchases depend on evidence of demand and the [purchase gate](docs/business-plan.md#printer-purchase-gate). Farm expansion is not a V1 software requirement.

| Document | Later use |
|---|---|
| [Target architecture](docs/target-architecture.md) | Candidate V2+ manufacturing capabilities and the conditions for introducing them |
| [Business plan](docs/business-plan.md) | Positioning, budget, capacity and illustrative economics; assumptions dated September 2026 |
| [Brand and websites](docs/brand-and-websites.md) | Brand relationship, visual direction and future commercial journeys |
| [PrintBack](docs/printback.md) | Future material collection and rewards program; entirely outside V1 |
| [Historical architecture](docs/history/architecture-2026-09-30.md) and [roadmap](docs/history/roadmap-2026-09-30.md) | Superseded plans retained for context; not implementation requirements |
