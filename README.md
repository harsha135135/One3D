# One3D by OneCreations

**V1: a focused Java backend portfolio project, built to demonstrate fresher-level engineering skills.**

One3D is the planned custom-manufacturing business of [OneCreations](https://github.com/harsha135135/OneCreations). Its operating software is called **PrintFlow**. V1 is implemented:
- **The application:** a Spring Boot and PostgreSQL backend with a Next.js customer and operator frontend, a separate durable printer simulator, private S3 uploads, Razorpay in test mode, and Cognito sign-in.
- **Operations:** Docker Compose, an on-demand AWS demo behind Nginx with HTTPS, a GitHub Actions pipeline, Terraform, Prometheus and Grafana, and tested backup and restore.
- **Evidence:** each checkpoint has an evidence record ([implementation status](IMPLEMENTED.md)).

V1 is **not yet accepted**. Checkpoint 1 was signed off by the owner. At the owner's request, an assistant implemented Checkpoints 3–9 (and Checkpoint 2's attempt limit). The owner's reviews of Checkpoints 2–9 were deferred until V1 was implemented, and are the next step.

Status: **implementation ready; personal review and signoff pending** · Updated: 2026-10-13

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

## Stack

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

Each was used in V1; what was shown, and its limits, is in the [implementation status](IMPLEMENTED.md) and the evidence records. They become résumé claims only after the owner's review ([résumé gate](docs/resume-evidence.md)). The [scope](docs/v1-scope.md) defines the minimum evidence for each required tool. **Ansible, Jenkins, RabbitMQ, Kafka and local Kubernetes are optional labs.** They must not become prerequisites for finishing V1. There is no required microservice split, message broker, Kubernetes cluster or multi-region deployment.

## Run it locally

These commands were run between 2026-10-07 and 2026-10-13; results are in the [Checkpoint 1](docs/evidence/2026-10-07-checkpoint-1.md), [2](docs/evidence/2026-10-09-checkpoint-2.md), [3](docs/evidence/2026-10-09-checkpoint-3.md), [4](docs/evidence/2026-10-09-checkpoint-4.md), [5](docs/evidence/2026-10-10-checkpoint-5.md), [6](docs/evidence/2026-10-12-checkpoint-6.md), [7](docs/evidence/2026-10-12-checkpoint-7.md), [8](docs/evidence/2026-10-12-checkpoint-8.md) and [9](docs/evidence/2026-10-13-checkpoint-9.md) evidence. The [five-minute demo](docs/demo.md) has a script and a recording.

**Prerequisites:** JDK 21 (checked with 21.0.12; point `JAVA_HOME` at it if your default JDK differs), Node 24 LTS for the frontend (`frontend/.nvmrc`) and a running Docker daemon. Maven is not required: use the included `./mvnw` wrapper.

```bash
# 1. Start PostgreSQL (127.0.0.1:5433), the printer simulator (127.0.0.1:8090),
#    the local mock identity provider (localhost:8180) and private file storage
#    (S3Mock on 127.0.0.1:9090; storage-init switches on bucket versioning)
docker compose up -d --build db simulator idp storage storage-init

# 2. Run each test suite from its own directory
(cd simulator && ./mvnw verify)   # simulator unit and HTTP tests
(cd backend && ./mvnw verify)     # backend unit tests (Surefire) and integration tests
                                  # (*IT, Failsafe; PostgreSQL, the simulator and S3Mock run as containers)
(cd frontend && npm ci && npm run lint && npm run build && npm run typecheck \
   && npx playwright install chromium && npm run test:e2e)   # browser smoke tests, API mocked

# 3. Start the API on http://127.0.0.1:8080 in ONE of two payment modes:
#    a) Razorpay TEST MODE: put RAZORPAY_KEY_ID (rzp_test_...), RAZORPAY_KEY_SECRET
#       and RAZORPAY_WEBHOOK_SECRET in the untracked .env, then export them
#       (they are read from the environment; never pass them as -D flags)
(cd backend && set -a && . ../.env && set +a && ONE3D_RAZORPAY_ENABLED=true ./mvnw spring-boot:run)
#    b) without keys: the demo-only shortcut confirms orders without payment
(cd backend && ./mvnw spring-boot:run -Dspring-boot.run.profiles=demo)
#    The two cannot be combined: startup fails if the demo profile is used with
#    Razorpay enabled, or if a key id is not a test-mode key.

# 4. In another terminal, start the frontend and open http://localhost:3000
(cd frontend && npm ci && npm run dev)
```

### The whole stack in containers

From a clean checkout, with only Git and Docker installed:

```bash
git clone https://github.com/harsha135135/One3D.git && cd One3D
docker compose up -d --build   # PostgreSQL, simulator, mock identity provider, storage,
                               # backend, frontend and Nginx; the first build takes a few minutes
# then open http://localhost:8088
```

These are the images that run on AWS, behind a local Nginx with the deployed routing, published on 127.0.0.1 only. The backend uses the demo payment shortcut, so no keys are needed. Metrics come with it: Grafana's "One3D overview" dashboard on http://localhost:3001 (admin / `local-dev-only`) and Prometheus on http://localhost:9091. The backend's health and metrics are on its management port (8081), which is not published; its logs are JSON lines (`docker compose logs backend`). Sign in as described below. Recreating the containers (`docker compose up -d --force-recreate`) or stopping them (`docker compose stop`) keeps the data in Docker volumes; `docker compose down -v` deletes it. This uses the same ports as the steps above, so run one or the other.

### The deployed demo

[https://demo.one3d.store](https://demo.one3d.store) runs on demand and is usually stopped to save credits, so it may not answer. It runs the same images behind Nginx with a Let's Encrypt certificate, with sign-in through Amazon Cognito (an authenticator app is required) and payments in Razorpay test mode. How it is built, deployed, started, stopped and destroyed is in [infra/README.md](infra/README.md); the decision and its costs are in [decision 0003](docs/decisions/0003-aws-demo-deployment.md).

### Using it

Sign in with **any username** on the mock provider's form. Usernames starting with `operator` (for example `operator-1`) get the operator role and the Operations pages; any other name is a customer. The frontend forwards `/api`, `/oauth2`, `/login` and `/logout` to the backend, so the session and CSRF cookies stay on one origin. It also forwards `/s3` to the local storage, so the browser uploads and downloads model files with presigned URLs on the same origin (on AWS the browser talks to S3 directly).

To try a custom part: open **Custom part**, choose an `.stl` file (at most 20 MB), and send it with a material and colour. Then sign in as an operator in another browser, open **Requests**, download the file, and send a quote. Accepting the quote creates an order that is paid and produced like a catalog order.

To pay in Razorpay test mode: on the order page choose **Pay with Razorpay (test mode)**. In Razorpay's window use a test card from Razorpay's documentation (for example Visa `4100 2800 0000 1007`, any future expiry and CVV) and choose **Success** on its demo bank page. The order page then asks the backend, which checks with Razorpay; no real money moves. Webhooks cannot reach a local machine, so locally the backend's own check confirms the payment; on the deployed demo, Razorpay's webhook does too.

Every `/api` call except the catalog needs a signed-in session, and state-changing calls also need the `X-XSRF-TOKEN` header (the frontend handles both). Customers see only their own orders, files and requests; `/api/ops/**` is for operators. The integration tests start their own PostgreSQL, simulator and S3Mock containers (the simulator image is built from `simulator/Dockerfile` on first use), so step 2 does not need step 1. A background worker in the backend delivers start commands to the simulator, polls running prints and checks uploaded files, once per second. Stop the local services with `docker compose stop`; their data stays in the `one3d-db`, `one3d-simulator` and `one3d-storage` volumes. The same suites run in GitHub Actions on every pull request.

| Method and path | Purpose |
|---|---|
| `GET /api/catalog/configurations` | List orderable configurations (one seeded demo item) |
| `POST /api/orders` | Place a one-line order: `{"productConfigurationId": "…", "quantity": 1}` returns 201 and a `Location` header. `quantity` may be omitted or null (it defaults to 1); any other value, including a fractional number such as `1.5`, returns 400 |
| `GET /api/orders/{id}` | Read one of your own orders; another customer's order is 404 |
| `GET /api/orders?page=0&size=20` | Your orders, newest first (size at most 50) |
| `GET /api/me` | Who is signed in, their roles, and whether the demo payment is enabled |
| `POST /logout` | Sign out (204); the session ends on the server |

Custom parts (Checkpoint 5), for the signed-in customer:

| Method and path | Purpose |
|---|---|
| `POST /api/files/uploads` | Start an upload: `{"filename": "part.stl", "sizeBytes": 123}` (an `.stl` name, 1 to 20,971,520 bytes) returns 201 with a presigned `PUT` URL and the headers to send. Larger declarations return 400 `FILE_TOO_LARGE` |
| `POST /api/files/{id}/finalize` | After the PUT: checks the stored size and pins the exact stored version (`VERIFYING`); a background check then makes it `VERIFIED` (with its SHA-256) or `REJECTED` |
| `GET /api/files/{id}`, `GET /api/files/{id}/download` | Your file's state; a redirect to a 5-minute link for the exact checked version, served as an attachment |
| `POST /api/requests` | Submit a checked file with requirements: `{"artifactId": "…", "material": "PETG", "colour": "Orange", "notes": "…"}` |
| `POST /api/requests/{id}/revisions` | A new file and requirements: review starts again and any open quote is withdrawn |
| `GET /api/requests`, `GET /api/requests/{id}`, `GET /api/requests/{id}/quotes` | Your requests, one request with every revision, its quotes |
| `POST /api/orders/{id}/checkout` | Razorpay test mode: open (or reuse) a checkout for exactly the order total; returns the public key id and Razorpay order id for Checkout. 409 `PAYMENT_WINDOW_CLOSED` after the order's payment window; 503 if Razorpay cannot be reached |
| `POST /api/orders/{id}/payment-check` | Ask Razorpay (server-side) for the order's payments and record captured ones; the browser's own report is never used |
| `GET /api/orders/{id}/timeline` | What has happened to your order, from recorded facts |
| `POST /api/payments/razorpay/webhook` | **Razorpay only.** No session or CSRF token; accepted only with a valid `X-Razorpay-Signature` over the raw body and an `X-Razorpay-Event-Id` |
| `POST /api/quotes/{id}/accept` | Create the order for an open, unexpired quote of the latest revision; accepting again returns the same order. Expired: 409 `QUOTE_EXPIRED`; withdrawn: 409 `QUOTE_WITHDRAWN` |

Operator endpoints (Checkpoints 2–6), operators only:

| Method and path | Purpose |
|---|---|
| `GET /api/ops/orders?status=CONFIRMED` | Operator queue: orders by state, oldest first, each with its job |
| `GET /api/ops/orders/{id}` | Any order, with its job |
| `POST /api/demo/orders/{id}/simulate-payment` | **Demo profile only (never together with Razorpay or on AWS); customers, own order only.** Confirm an order without payment; the order records `confirmationSource: DEMO_SIMULATED` and its timeline says no payment was taken |
| `POST /api/ops/orders/{id}/release` | Create the order's production job (order must be `CONFIRMED`) |
| `POST /api/ops/jobs/{id}/attempts` | Record the start of the next attempt: `{"printerId": "…"}`. The attempt is `START_INTENT` until the worker has delivered it and the simulator confirms (`RUNNING`). A lost reply makes it `RECONCILIATION_REQUIRED`; it keeps the printer until the simulator's journal answers. Needs a free printer with a cleared bed. At most 3 attempts per job, counting print failures and QC failures; starts the printer refused do not count; a 4th returns 409 `ATTEMPT_LIMIT_REACHED` |
| `POST /api/ops/attempts/{id}/qc` | Whole-part inspection: `{"result": "PASSED" or "FAILED", "notes": "…"}` |
| `POST /api/ops/printers/{id}/clear-bed` | Operator confirms the print was removed: `{"attemptId": "…"}`, which must be the printer's `bedCycleAttemptId`. Nothing clears the bed automatically; a clearance for an earlier print returns 409 `STALE_CLEARANCE` |
| `POST /api/ops/attempts/{id}/reconcile` | Retry delivery that ran out of automatic tries (it re-checks the same command; it never starts a new attempt) |
| `GET /api/ops/work?status=EXHAUSTED` | Background work that needs an operator: start deliveries and file checks whose tries ran out |
| `POST /api/ops/files/{id}/retry-verification` | Try a file check again after its tries ran out |
| `GET /api/ops/requests?status=SUBMITTED` | Custom requests by state, longest waiting first |
| `GET /api/ops/requests/{id}`, `GET /api/ops/requests/{id}/revisions/{n}/file` | A request with every revision; a redirect to a 5-minute download link for revision n's exact file |
| `POST /api/ops/requests/{id}/request-changes`, `/reject` | `{"message": "…"}` to the customer |
| `POST /api/ops/requests/{id}/quotes` | Quote the latest revision: `{"revisionId", "priceMinor", "validDays" (1–14), "material", "layerHeightMicrons", "recipeNotes"}`; an open quote is withdrawn |
| `POST /api/ops/orders/{id}/ready-for-handoff` | Only after the job's part passed QC |
| `POST /api/ops/orders/{id}/handed-over` | The customer collected the part (only after ready for handoff) |
| `GET /api/ops/payments`, `POST /api/ops/payments/{id}/resolve` | Captured payments that need a manual check (unknown order, wrong amount, late, or a second payment); record what was decided with `{"note": "…"}`. No money moves |
| `GET /api/ops/jobs/{id}`, `GET /api/ops/printers` | Job with all attempts; printer ownership and bed state |

The seeded printer `0d3d0000-0000-4000-8000-000000000101` is simulated by the separate `simulator/` process; no real printer is controlled. Its results (completion or failure) come from the simulator, not from this API. For local development the simulator has dev controls on 127.0.0.1:8090: `POST /dev/printers/{id}/complete`, `POST /dev/printers/{id}/fail` with `{"reason": "…"}`, and `POST /dev/printers/{id}/faults` with `{"dropNextStartAck": true}`. An invalid step returns 409 with a code such as `BED_NOT_CLEAR`, `PRINTER_BUSY` or `JOB_NOT_ACCEPTED`. Two operators acting at once get exactly one success and a 409.

```bash
curl -s -X POST http://127.0.0.1:8080/api/orders \
  -H "Content-Type: application/json" \
  -d '{"productConfigurationId":"0d3d0000-0000-4000-8000-000000000002","quantity":1}'
```

More requests, including invalid ones, are in [`backend/api.http`](backend/api.http). Errors are `application/problem+json` responses with a stable `code` (for example `VALIDATION_FAILED`, `CONFIGURATION_NOT_ORDERABLE` or `ORDER_NOT_FOUND`).

Locally, every service listens on 127.0.0.1 (the identity provider on localhost) and is never published to other machines. On the deployed demo only Nginx is reachable, on ports 80 and 443, and Cognito replaces the mock identity provider.

## V1 completion and the next step

V1 is complete only when the required workflow, failure tests, CI/deployment/restore checks and documentation work, and Harsha can explain, change and debug the critical code. The owner then accepts V1 and updates the résumé using recorded evidence. An agent cannot approve the owner's understanding on their behalf.

After that gate, agree one small V2 increment based on real business needs. Do not start implementing the full future architecture simply because V1 code builds.

Add run, test and demo commands for later checkpoints only after they have been verified. Do not present planned commands as runnable instructions.

## Websites and brand

| Address | Purpose |
|---|---|
| `onecreations.in` | Creative studio and public portfolio, maintained in its separate repository |
| `demo.one3d.store` | The V1 demonstration on AWS, started on demand and usually stopped (see [the demo script](docs/demo.md)). Its operator screens are under `/ops`, protected by backend authorization |
| `one3d.store` | Intended future One3D customer application; not deployed |

`one3d.store` itself is an intended destination, not a deployment. One3D uses the Vantablack direction recorded in [brand and websites](docs/brand-and-websites.md). OneCreations uses Matte Black. The visual direction does not expand V1 into a full commercial storefront.

## Future business context

The business starts with one Bambu Lab X1C without AMS and a ₹3–4 lakh total first-year budget. Printer purchases depend on evidence of demand and the [purchase gate](docs/business-plan.md#printer-purchase-gate). Farm expansion is not a V1 software requirement.

| Document | Later use |
|---|---|
| [Target architecture](docs/target-architecture.md) | Candidate V2+ manufacturing capabilities and the conditions for introducing them |
| [Business plan](docs/business-plan.md) | Positioning, budget, capacity and illustrative economics; assumptions dated September 2026 |
| [Brand and websites](docs/brand-and-websites.md) | Brand relationship, visual direction and future commercial journeys |
| [PrintBack](docs/printback.md) | Future material collection and rewards program; entirely outside V1 |
| [Historical architecture](docs/history/architecture-2026-09-30.md) and [roadmap](docs/history/roadmap-2026-09-30.md) | Superseded plans retained for context; not implementation requirements |
