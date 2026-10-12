# Evidence: Checkpoint 8: Terraform, monitoring and recovery

```text
Checkpoint / claim:  Roadmap Checkpoint 8. The backend exposes a small set of Prometheus metrics
                     (API errors and latency, background-work age, uncertain starts, simulator
                     failures) on a private management port, with labels that never carry ids.
                     Logs are JSON lines with request, order, job, attempt, command or file ids.
                     Prometheus and one provisioned Grafana dashboard run next to the
                     application, reachable only privately. A stalled print start can be found
                     from the dashboard and the logs. A fresh backup restores into a separate
                     database with its records and file versions intact. On AWS: the same, plus
                     recreating the disposable instance with Terraform and the cost record.
Date:                2026-10-12
Code revision:       feat/checkpoint-8 (part 1; this record lists the commits in its PR), on main 188b1f2
Environment:         macOS (arm64), OpenJDK 21.0.12, Spring Boot 4.1.1 (Actuator, Micrometer 1.17
                     with the Prometheus registry), Docker Engine 29.6.2, Compose 5.3.1,
                     Prometheus v3.15.0, Grafana 12.4.12, PostgreSQL 17, Playwright 1.64
Implemented by:      the assistant, under the owner's instruction of 2026-10-09
Simulated:           the printer is the simulator; the fault is the simulator container being stopped.
                     Local runs use the mock identity provider, the demo payment shortcut and S3Mock.
Owner review:        deferred by the owner until V1 is complete
```

This is part 1: the implementation, checked locally. Part 2 runs the exercises on the AWS demo after this merges (see "Owed").

## 1. Automated checks

| Suite | Command | Result |
|---|---|---|
| backend | `./mvnw clean verify` (in `backend/`) | **241 passed**: 55 unit, 186 integration, 29 s ([summary](2026-10-12-checkpoint-8/backend-test-reports/failsafe-summary.xml)) |
| simulator, frontend | unchanged | Run by CI on the pull request |

New and changed tests:

| Test | What it shows |
|---|---|
| `ManagementPortIT` (3, real HTTP servers) | Health and Prometheus metrics answer on the management port only. Health shows no details. `env`, `beans`, `heapdump`, `loggers`, `metrics`, `configprops` and the index page are refused or not found. The API port serves no Actuator endpoint |
| `ProductionMetricsIT` (2) | An unreachable simulator raises the unknown-result and unreachable counters and the uncertain-start gauge, and the warning carries `attempt_id`, `job_id` and `command_id`. Recovery: a started delivery, the gauge falls, a completed print is counted. **No meter has a UUID-shaped label value**; routes are templates such as `/api/orders/{orderId}` |
| `LogContextTest` (4) | Every request gets a `request_id`, returned as `X-Request-Id`. Nginx's id is kept if it is 8–64 letters, digits or dashes; anything else (spaces, a newline, too long) is replaced. Path ids enter the log context only if UUID-shaped |
| `FilesIT` (changed) | Nothing logged during an upload, a check and a download contains `X-Amz-Signature` or `X-Amz-Credential` |

**Negative controls** ([outputs](2026-10-12-checkpoint-8/negative-controls/)); each file was restored and checked with `cmp`:

| # | Change | Result |
|---|---|---|
| M1 | The management chain no longer matches the health and metrics endpoints | `ManagementPortIT` failed: 401 instead of 200 |
| M2 | A counter tagged with the attempt id | `noMetricLabelCarriesAnId` failed, naming the meter and the id |
| M3 | Any inbound `X-Request-Id` accepted | `LogContextTest` failed on the spaced, multi-line and over-long ids |

## 2. Rules and where they are enforced

| Rule | Enforced by | Proven by |
|---|---|---|
| Health and metrics are never public | `management.server.port: 8081`, not published by any Compose file and not routed by Nginx; only `health` and `prometheus` exposed; details never shown | `ManagementPortIT`; locally `127.0.0.1:8081` does not answer and `/actuator/*` through the site gives 404 (§3) |
| Metrics need no sign-in, so they must stay on the internal network | A separate security chain for the two endpoints, matched only on the management port | `ManagementPortIT`; control M1 |
| Labels never carry ids | Custom meters take enum tags only; HTTP metrics use route templates | `noMetricLabelCarriesAnId`; control M2 |
| Latency is measured with few series | Seven fixed buckets (50 ms to 5 s) instead of a histogram per route | `application.yaml`; the dashboard's p95 panel |
| Logs say which records a line is about, and nothing secret | `RequestIdFilter`, `PathIdLogContext` (UUID-shaped path ids only), ids in the workers' log context; Nginx logs the path without the query string | `LogContextTest`, `ProductionMetricsIT`, `FilesIT`; control M3 |
| Grafana and Prometheus are private | Not published on AWS except Grafana on the instance's 127.0.0.1:3001 (SSM port forwarding); no anonymous access or sign-up; the admin password comes from SSM, and the deploy stops without it | `compose.deploy.yaml`, `deploy.sh`; checked on AWS in part 2 |
| A configuration change reaches the running container | Prometheus and Grafana carry a hash label of `infra/monitoring`, like Nginx (Checkpoint 7). The deploy bundle now includes `infra/monitoring` | `deploy.sh`, `ci.yml`; checked on AWS in part 2 |

## 3. Local stack with monitoring

`docker compose up -d --build`: the whole stack, now including Prometheus and Grafana.
- **Prometheus:** both targets `up` (the backend's `:8081/actuator/prometheus` and Prometheus itself).
- **Privacy:** `127.0.0.1:8081` does not answer from the host. Through the site, `/actuator/health` and `/actuator/prometheus` give 404 and `/api/actuator/prometheus` gives 401.
- **Request ids:** a request through the local Nginx got `X-Request-Id: c07f9476…`. Nginx's access line shows `"GET /api/catalog/configurations HTTP/1.1" 200 … rid=c07f9476…`, and the backend's log line has the same `request_id`.
- **Logs:** the backend writes ECS JSON, for example `{"@timestamp":…,"log":{"level":"INFO",…},"service":{"name":"one3d-backend",…},"message":…}`.

**Memory** ([`local/memory.txt`](2026-10-12-checkpoint-8/local/memory.txt)), after the fault rehearsal:

| Container | Used / limit |
|---|---|
| backend | 466 / 640 MiB |
| Grafana | 140 / 192 MiB |
| Prometheus | 119 / 192 MiB |
| simulator | 134 / 192 MiB |

The other application containers each use less than 45 MiB. Whether this fits the 2 GiB instance is measured on AWS in part 2.

## 4. Fault rehearsal: a stalled start found through metrics and logs

[`local/fault-rehearsal.txt`](2026-10-12-checkpoint-8/local/fault-rehearsal.txt) ([script](2026-10-12-checkpoint-8/local/fault-rehearsal.sh)). Screenshots: [during the fault](2026-10-12-checkpoint-8/local/grafana-during-fault.png), [after recovery](2026-10-12-checkpoint-8/local/grafana-after-recovery.png).

1. **Fault (11:33:48 UTC):** an order was confirmed and released, the simulator container was stopped, and an operator started the print.
2. **What the operator sees (Prometheus, then the dashboard):**
   - `one3d_simulator_unreachable_total{operation="dispatch"}` and `one3d_dispatch_results_total{result="unknown"}` rose from 0 to 5: every try failed;
   - `one3d_attempts_awaiting_reconciliation` is 1, shown in red;
   - the oldest-work age climbed to about 27 s, then the row became EXHAUSTED (`one3d_work_items{kind="DISPATCH_START",status="EXHAUSTED"}` 1);
   - API errors and latency stayed normal, so the problem is behind the API, not in it.
3. **The logs for that attempt:** five JSON warnings, at 11:33:49, :51, :55, 11:34:03 and :20 (backoff 2, 4, 8, 16 s). Each has the same `attempt_id`, `command_id` and `job_id`, and the message "Start of attempt … not confirmed (command …): Simulator did not answer". The message ended in "null", because the underlying error had no text. It now names the kind of error (`SimulatorClient`); the rehearsal ran before that change.
4. **Recovery:** the simulator started again, the operator chose reconcile, and the worker asked the simulator's journal, which did not have the command. It then sent the start: RUNNING, then AWAITING_QC after completion. The gauge went back to 0, and `started` counted 1.

## 5. Restore check (local)

[`local/restore-check.txt`](2026-10-12-checkpoint-8/local/restore-check.txt), by `infra/ops/restore-check.sh --local`:
- **Backup and restore:** a fresh `pg_dump` (88 KB) restored into `one3d_restore_check`, with migrations 1–8.
- **Row counts:** the same in all 15 tables.
- **One order, compared field by field:** the newest handed-over order is identical in both databases. That covers the custom part, recipe revision 1, the Razorpay test payment (RECONCILIATION APPLIED 34900 INR), the job ACCEPTED, attempt 1 COMPLETED with QC PASSED, the command ACKNOWLEDGED and 6 timeline facts.
- **One file:** the newest verified file's record is identical. Its stored version, read by version id from storage, has the recorded SHA-256.
- **Clean-up:** the copy was dropped.

The procedure for restoring the live database (stop the application, restore, compare with the simulator's journal, then start again) is in [`infra/README.md`](../infra/README.md#backups-and-restoring-the-database).

## Owed (part 2, on AWS after this merges)

- **Terraform:**
  - apply the new Grafana password parameter, after inspecting the plan;
  - recreate the disposable instance with `-replace`. Guards: a fresh backup, a data-volume snapshot, a check that nothing else is deleted, and termination protection turned off and back on;
  - redeploy and recheck the records, files and sign-in;
  - explain the follow-up plan.
- **Monitoring on the instance:**
  - memory on the 2 GiB instance;
  - the dashboard through SSM port forwarding;
  - the fault exercise and the restore check;
  - edge checks extended to 8081, 3001 and 9091.
- **Cost:** Budgets' recorded spend and the run windows ([`runtime-windows.txt`](2026-10-12-checkpoint-8/runtime-windows.txt)). The billed total is final only in Checkpoint 9.

## Known limitations

- **Metrics are lost when the instance is replaced.** Prometheus keeps 15 days, capped at 512 MB, on the instance's root disk; they are not valuable data.
- **The management endpoints have no authentication.** They are reachable only on the internal network or the loopback interface.
- **Locally, after editing an Nginx or monitoring file, restart that container.** The hash labels apply only to the deploy.
- **The work-queue gauges are read from the database at most every 5 seconds,** when Prometheus scrapes.
