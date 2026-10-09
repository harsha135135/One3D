# One3D by OneCreations

**V1: a focused Java backend portfolio project, built to demonstrate fresher-level engineering skills.**

One3D is the planned custom-manufacturing business of [OneCreations](https://github.com/harsha135135/OneCreations). Its operating software is called **PrintFlow**. Checkpoint 1 of V1 (a catalog order API on Spring Boot and PostgreSQL) is implemented, tested locally and signed off by the owner. Checkpoint 2 (release, jobs, attempts, bed clearance and QC) Checkpoint 3 (a separate durable printer simulator, dispatch and reconciliation, protection against concurrent operators) and Checkpoint 4 (sign-in, roles, order ownership and the customer and operator frontend) are implemented and tested; their owner reviews are deferred until V1 is complete. Later checkpoints, infrastructure and deployment are not implemented yet, and V1 is not complete.

Status: Checkpoint 1 complete (signed off 2026-10-09; schema explanation reference-assisted, to revisit); Checkpoints 2–4 implemented and tested, owner reviews deferred until V1 is complete · Scope updated: 2026-10-07

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

These commands were run on 2026-10-07 and 2026-10-09; results are in the [Checkpoint 1](docs/evidence/2026-10-07-checkpoint-1.md), [2](docs/evidence/2026-10-09-checkpoint-2.md), [3](docs/evidence/2026-10-09-checkpoint-3.md) and [4](docs/evidence/2026-10-09-checkpoint-4.md) evidence.

**Prerequisites:** JDK 21 (checked with 21.0.12; point `JAVA_HOME` at it if your default JDK differs), Node 24 LTS for the frontend (`frontend/.nvmrc`) and a running Docker daemon. Maven is not required: use the included `./mvnw` wrapper.

```bash
# 1. Start PostgreSQL (127.0.0.1:5433), the printer simulator (127.0.0.1:8090)
#    and the local mock identity provider (localhost:8180)
docker compose up -d --build db simulator idp

# 2. Run each test suite from its own directory
(cd simulator && ./mvnw verify)   # simulator unit and HTTP tests
(cd backend && ./mvnw verify)     # backend unit tests (Surefire) and integration tests
                                  # (*IT, Failsafe; PostgreSQL and the simulator run as containers)
(cd frontend && npm ci && npm run lint && npm run build && npm run typecheck \
   && npx playwright install chromium && npm run test:e2e)   # browser smoke tests, API mocked

# 3. Start the API on http://127.0.0.1:8080 with the demo-only simulated
#    payment (needed to walk an order through production before Checkpoint 6)
(cd backend && ./mvnw spring-boot:run -Dspring-boot.run.profiles=demo)

# 4. In another terminal, start the frontend and open http://localhost:3000
(cd frontend && npm ci && npm run dev)
```

Sign in with **any username** on the mock provider's form. Usernames starting with `operator` (for example `operator-1`) get the operator role and the Operations pages; any other name is a customer. The frontend forwards `/api`, `/oauth2`, `/login` and `/logout` to the backend, so the session and CSRF cookies stay on one origin.

Every `/api` call except the catalog needs a signed-in session, and state-changing calls also need the `X-XSRF-TOKEN` header (the frontend handles both). Customers see only their own orders; `/api/ops/**` is for operators. The integration tests start their own PostgreSQL and simulator containers (the simulator image is built from `simulator/Dockerfile` on first use), so step 2 does not need step 1. A background worker in the backend delivers start commands to the simulator and polls running prints once per second. Stop the local services with `docker compose stop`; their data stays in the `one3d-db` and `one3d-simulator` volumes. The same suites run in GitHub Actions on every pull request.

| Method and path | Purpose |
|---|---|
| `GET /api/catalog/configurations` | List orderable configurations (one seeded demo item) |
| `POST /api/orders` | Place a one-line order: `{"productConfigurationId": "…", "quantity": 1}` returns 201 and a `Location` header. `quantity` may be omitted or null (it defaults to 1); any other value, including a fractional number such as `1.5`, returns 400 |
| `GET /api/orders/{id}` | Read one of your own orders; another customer's order is 404 |
| `GET /api/orders?page=0&size=20` | Your orders, newest first (size at most 50) |
| `GET /api/me` | Who is signed in, their roles, and whether the demo payment is enabled |
| `POST /logout` | Sign out (204); the session ends on the server |

Operator endpoints (Checkpoints 2–4), operators only:

| Method and path | Purpose |
|---|---|
| `GET /api/ops/orders?status=CONFIRMED` | Operator queue: orders by state, oldest first, each with its job |
| `GET /api/ops/orders/{id}` | Any order, with its job |
| `POST /api/demo/orders/{id}/simulate-payment` | **Demo profile only; customers, own order only.** Confirm an order without payment; the order records `confirmationSource: DEMO_SIMULATED` |
| `POST /api/ops/orders/{id}/release` | Create the order's production job (order must be `CONFIRMED`) |
| `POST /api/ops/jobs/{id}/attempts` | Record the start of the next attempt: `{"printerId": "…"}`. The attempt is `START_INTENT` until the worker has delivered it and the simulator confirms (`RUNNING`). A lost reply makes it `RECONCILIATION_REQUIRED`; it keeps the printer until the simulator's journal answers. Needs a free printer with a cleared bed. At most 3 attempts per job, counting print failures and QC failures; starts the printer refused do not count; a 4th returns 409 `ATTEMPT_LIMIT_REACHED` |
| `POST /api/ops/attempts/{id}/qc` | Whole-part inspection: `{"result": "PASSED" or "FAILED", "notes": "…"}` |
| `POST /api/ops/printers/{id}/clear-bed` | Operator confirms the print was removed: `{"attemptId": "…"}`, which must be the printer's `bedCycleAttemptId`. Nothing clears the bed automatically; a clearance for an earlier print returns 409 `STALE_CLEARANCE` |
| `POST /api/ops/attempts/{id}/reconcile` | Retry delivery that ran out of automatic tries (it re-checks the same command; it never starts a new attempt) |
| `GET /api/ops/work?status=EXHAUSTED` | Delivery work that needs an operator |
| `POST /api/ops/orders/{id}/ready-for-handoff` | Only after the job's part passed QC |
| `GET /api/ops/jobs/{id}`, `GET /api/ops/printers` | Job with all attempts; printer ownership and bed state |

The seeded printer `0d3d0000-0000-4000-8000-000000000101` is simulated by the separate `simulator/` process; no real printer is controlled. Its results (completion or failure) come from the simulator, not from this API. For local development the simulator has dev controls on 127.0.0.1:8090: `POST /dev/printers/{id}/complete`, `POST /dev/printers/{id}/fail` with `{"reason": "…"}`, and `POST /dev/printers/{id}/faults` with `{"dropNextStartAck": true}`. An invalid step returns 409 with a code such as `BED_NOT_CLEAR`, `PRINTER_BUSY` or `JOB_NOT_ACCEPTED`. Two operators acting at once get exactly one success and a 409.

```bash
curl -s -X POST http://127.0.0.1:8080/api/orders \
  -H "Content-Type: application/json" \
  -d '{"productConfigurationId":"0d3d0000-0000-4000-8000-000000000002","quantity":1}'
```

More requests, including invalid ones, are in [`backend/api.http`](backend/api.http). Errors are `application/problem+json` responses with a stable `code` (for example `VALIDATION_FAILED`, `CONFIGURATION_NOT_ORDERABLE` or `ORDER_NOT_FOUND`).

Locally, every service listens on 127.0.0.1 (the identity provider on localhost) and is never published to other machines. The HTTPS proxy and the public demo come in Checkpoint 7, where Cognito replaces the mock identity provider.

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
