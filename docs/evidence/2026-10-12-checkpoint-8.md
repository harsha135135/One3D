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
Code revision:       Part 1: a3f34f7, 613eb9f, ff4d227, 5a3f52c (PR #13, merged as 41f85c5).
                     Part 2 on feat/checkpoint-8-cloud: one added test and this record.
Deployed revision:   41f85c5, on i-08b860e0cf0061856 and then on its replacement
                     i-026fb29f6b8d6591a (2026-10-12, 15:11–15:40 UTC)
Environment:         macOS (arm64), OpenJDK 21.0.12, Spring Boot 4.1.1 (Actuator, Micrometer 1.17
                     with the Prometheus registry), Docker Engine 29.6.2, Compose 5.3.1,
                     Prometheus v3.15.0, Grafana 12.4.12, PostgreSQL 17, Playwright 1.64
Implemented by:      the assistant, under the owner's instruction of 2026-10-09
Simulated:           the printer is the simulator; the fault is the simulator container being stopped.
                     Local runs use the mock identity provider, the demo payment shortcut and S3Mock.
Owner review:        deferred by the owner until V1 is complete
```

Part 1 (§1–§5) is the implementation, checked locally. Part 2 (§6–§11) is the exercises on the AWS demo after part 1 merged.

## 1. Automated checks

| Suite | Command | Result |
|---|---|---|
| backend | `./mvnw clean verify` (in `backend/`) | Part 1: **241 passed** (55 unit, 186 integration, 29 s; [summary](2026-10-12-checkpoint-8/backend-test-reports/failsafe-summary.xml)). With part 2's added test: **242 passed** in CI ([run 38272324659](https://github.com/harsha135135/One3D/actions/runs/38272324659) on `811df11`: 55 unit, 187 integration) |
| simulator, frontend | unchanged | Run by CI on the pull request |

New and changed tests:

| Test | What it shows |
|---|---|
| `ManagementPortIT` (3, real HTTP servers) | Health and Prometheus metrics answer on the management port only. Health shows no details. `env`, `beans`, `heapdump`, `loggers`, `metrics`, `configprops` and the index page are refused or not found. The API port serves no Actuator endpoint |
| `ProductionMetricsIT` (2) | An unreachable simulator raises the unknown-result and unreachable counters and the uncertain-start gauge, and the warning carries `attempt_id`, `job_id` and `command_id`. Recovery: a started delivery, the gauge falls, a completed print is counted. **No meter has a UUID-shaped label value**; routes are templates such as `/api/orders/{orderId}` |
| `LogContextTest` (4) | Every request gets a `request_id`, returned as `X-Request-Id`. Nginx's id is kept if it is 8–64 letters, digits or dashes; anything else (spaces, a newline, too long) is replaced. Path ids enter the log context only if UUID-shaped |
| `RazorpayWebhookIT.theWebhookLogLinesCarryTheRequestIdFromNginx` (added in part 2) | The webhook's log lines carry the request id Nginx sent, and the response returns it |
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
- **Request ids:** a request through the local Nginx got `X-Request-Id: c07f9476…`, and Nginx's access line shows `"GET /api/catalog/configurations HTTP/1.1" 200 … rid=c07f9476…`. *Correction (part 2):* part 1 also said the backend's log line had the same `request_id`. That was not shown: the catalog request writes no backend log line, and the line printed was the servlet's start-up message, written outside any request. It is shown in §9, with real Razorpay webhook requests on AWS, and tested by `RazorpayWebhookIT.theWebhookLogLinesCarryTheRequestIdFromNginx`.
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

## 6. Terraform: inspect, apply, recreate the instance, follow-up plan

**The plan for part 1's change** ([`cloud/plan-grafana-parameter.txt`](2026-10-12-checkpoint-8/cloud/plan-grafana-parameter.txt)):
- it adds the Grafana password and its SSM parameter: 2 to add, nothing changed or destroyed, and no drift;
- it was saved, applied as saved, and the parameter exists as a SecureString.

**Recreating the disposable instance**, in three phases with [`cloud/replace-instance.sh`](2026-10-12-checkpoint-8/cloud/replace-instance.sh):

1. **Prepare** ([`replace-1-prepare.txt`](2026-10-12-checkpoint-8/cloud/replace-1-prepare.txt)):
   - `demo.sh stop` (a backup, then the instance stopped);
   - a snapshot of the data volume, as a second copy;
   - termination protection off;
   - the plan `-replace=aws_instance.demo` saved: 2 to add, 2 to change, 2 to destroy;
   - a guard on the saved plan's JSON: **only `aws_instance.demo` and `aws_volume_attachment.data` are deleted**. The Elastic IP and the GitHub role's policy are updated in place. The data volume, buckets, images, Cognito and parameters are untouched.
2. **Apply** ([`replace-2-apply.txt`](2026-10-12-checkpoint-8/cloud/replace-2-apply.txt)):
   - `i-08b860e0cf0061856` was terminated and `i-026fb29f6b8d6591a` created, with the same AMI and the same Elastic IP;
   - the first boot mounted the existing data volume **without formatting it**: XFS, the PostgreSQL 17 files, and the certificate for `demo.one3d.store`;
   - `DEMO_INSTANCE_ID` was updated in GitHub, and termination protection is on again. AWS reported it a minute later; the check right after the apply still read false.
3. **Deploy** ([`replace-3-deploy.txt`](2026-10-12-checkpoint-8/cloud/replace-3-deploy.txt)):
   - "Deploy a commit" ran with `41f85c5`;
   - "Certificate not yet due for renewal": the certificate came from the data volume, with no new issuance;
   - "previous: none": the root disk is new;
   - the site answered 200.

**After the replacement:**
- **Records:** signing in through Cognito, order `3627d7c0` is HANDED_OVER and file `3f45dde5` VERIFIED ([`recheck-after-replacement.txt`](2026-10-12-checkpoint-8/cloud/recheck-after-replacement.txt)). Database row counts are the same as before ([`rows-after-replacement.txt`](2026-10-12-checkpoint-8/cloud/rows-after-replacement.txt)).
- **Edge checks:** they all pass again ([`edge-checks-after-replacement.txt`](2026-10-12-checkpoint-8/cloud/edge-checks-after-replacement.txt)).
- **Follow-up plan:** **no changes** (`-detailed-exitcode` 0; 64 resources) ([`plan-after-replacement.txt`](2026-10-12-checkpoint-8/cloud/plan-after-replacement.txt)). Nothing drifted, because the AMI and the first-boot script are deliberately ignored after creation.
- **Snapshot:** deleted once these checks passed, because Terraform does not manage it.

**State handling:**
- the state is in S3 (versioned, encrypted, private) with a lock file;
- nothing of it is in Git (`*.tfstate*` and `*.tfplan` are ignored);
- saved plans were kept outside the repository, because they contain sensitive values.

## 7. Monitoring on the instance

[`cloud/monitoring-checks.txt`](2026-10-12-checkpoint-8/cloud/monitoring-checks.txt), and the same after the replacement ([`…-after-replacement.txt`](2026-10-12-checkpoint-8/cloud/monitoring-checks-after-replacement.txt)):
- **From the internet:** only 80 and 443 accept connections. 22, 3000, 3001, 5432, 8080, **8081**, 8090, 9090 and 9091 do not.
- **Actuator through the site:** `/actuator/*` gives 404, `/api/actuator/*` gives 401, and no metrics appear in any body.
- **On the instance:** sockets listen on 80, 443 and 22 (sshd, which the security group does not open; there is no key pair) and on **127.0.0.1:3001** (Grafana) only.
- **Prometheus:** it scrapes `http://backend:8081/actuator/prometheus` (up); there are 16 One3D series.
- **Grafana without signing in:** `/api/search`, the dashboard and the data sources answer 401.
- **Memory** with monitoring, on the 2 GiB t3.small:

  | | Used | Available | Swap used |
  |---|---|---|---|
  | After deploying | 939 MiB | 797 MiB | 14 MiB |
  | After the replacement | 966 MiB | 728 MiB | 17 MiB |

  By container: backend 365 MiB, Grafana 134 MiB, the simulator 52 MiB, the database 51 MiB, Prometheus 47 MiB, the frontend 43 MiB, Nginx 6 MiB. It fits; no larger instance is needed.
- **Disks:** the root disk is 44% used, the data volume 3%.
- **The dashboard** was opened through SSM port forwarding (`AWS-StartPortForwardingSession` to 127.0.0.1:3001), signed in with the password from SSM.

## 8. Fault exercise on AWS: found through metrics and logs

[`cloud/fault-exercise.txt`](2026-10-12-checkpoint-8/cloud/fault-exercise.txt) ([script](2026-10-12-checkpoint-8/cloud/fault-exercise.cjs)). Screenshots, taken through the port forward: [during the fault](2026-10-12-checkpoint-8/cloud/grafana-during-fault.png), [after recovery](2026-10-12-checkpoint-8/cloud/grafana-after-recovery.png).

1. **Setup:** the demo operator, signed in through Cognito with TOTP, released order `3627d7c0`, paid minutes earlier in hosted Checkout.
2. **Fault (15:19:26 UTC):** the simulator container was stopped through SSM, and the operator started the print.
3. **Metrics** after 65 s:
   - `one3d_simulator_unreachable_total{operation="dispatch"}` 5;
   - `one3d_dispatch_results_total{result="unknown"}` 5;
   - `one3d_attempts_awaiting_reconciliation` 1;
   - one exhausted start delivery;
   - **0** API 5xx. The fault is behind the API, not in it.
4. **Logs:** five JSON warnings with the attempt's `attempt_id`, `command_id` and `job_id` (15:19:32 to 15:20:02, backoff doubling), each "Simulator did not answer: **ConnectException**". Part 1's fix replaced "null" here.
5. **Recovery (15:21:34):**
   - the simulator was started and the operator reconciled;
   - the worker found no record of the command in the journal and sent it;
   - RUNNING, then completed (`simulator.sh complete` through SSM), QC passed, ready for handoff and handed over;
   - the gauge was back to 0, and `started` counted 1.

## 9. One request followed from Nginx to the backend

[`cloud/request-correlation.txt`](2026-10-12-checkpoint-8/cloud/request-correlation.txt) ([script](2026-10-12-checkpoint-8/cloud/request-correlation.sh)).
- **The payment:** order `3627d7c0` was paid in hosted Checkout with Razorpay's test card ([`pay-cp8.txt`](2026-10-12-checkpoint-8/cloud/pay-cp8.txt)). There were 0 CSP violations, and `payment.captured` was APPLIED by the webhook.
- **Following the requests:** for each of Razorpay's three webhook requests, Nginx's access line (`POST /api/payments/razorpay/webhook … rid=<id>`) and the backend's JSON lines carry the same id as `request_id`:
  - `payment.authorized`: IGNORED;
  - `payment.captured`: "Payment … confirmed order 3627d7c0…", then APPLIED;
  - `order.paid`: ALREADY_RECORDED.

## 10. Restore check on AWS

[`cloud/restore-check.txt`](2026-10-12-checkpoint-8/cloud/restore-check.txt), by `infra/ops/restore-check.sh` (it runs on the instance through SSM):
- **The backup:** a fresh `pg_dump` (70 KB) was stored in the ops bucket, read back from S3, and restored into `one3d_restore_check`, with migrations 1–8.
- **Row counts:** the same in all 15 tables.
- **One order:** `3627d7c0` is identical in both databases, with everything it links to (WEBHOOK APPLIED 49900 INR, job ACCEPTED, attempt COMPLETED with QC PASSED, command ACKNOWLEDGED, 6 timeline facts).
- **One file:** file `3f45dde5`'s exact S3 version, read by the instance role by version id, has the restored record's SHA-256.
- **Clean-up:** the copy was dropped.

## 11. Resources, runtime and cost

- **Resources:**
  - one t3.small (2 vCPU, 2 GiB) with a 12 GB gp3 root disk and a separate 4 GB gp3 data volume;
  - one Elastic IP;
  - two S3 buckets, three ECR repositories and one Cognito user pool, all in ap-south-1.
- **Runtime on 2026-10-12:** about 2.3 instance-hours in seven windows ([`runtime-windows.txt`](2026-10-12-checkpoint-8/runtime-windows.txt)). The demo is stopped between uses.
- **AWS Budgets at 15:39 UTC** ([`cloud/budgets.txt`](2026-10-12-checkpoint-8/cloud/budgets.txt)):
  - usage before credits this month is **US$0.53** for the whole account, One3D and OneCreations together;
  - **US$0.00** out of pocket.
  - Budgets updates a few times a day and billing lags, so the final figure for the demo period is recorded in Checkpoint 9.
- **Comparison with the estimate:** decision 0003 estimates about $0.18 a day stopped plus $0.0224 an hour running, which is about $0.23 for this first day. The Budgets figure cannot be compared with that yet: Budgets lags by up to a day, so it probably does not include all of the 12th's usage, and it covers OneCreations too. The comparison is made in Checkpoint 9 from a later reading. **No saving is claimed.**
- **Cleanup result:** each exercise cleaned up after itself:
  - the restore copies were dropped;
  - the replaced instance and its root disk were terminated;
  - the data-volume snapshot was deleted after the checks.
  A read-only inventory afterwards ([`cloud/inventory-after-checkpoint-8.txt`](2026-10-12-checkpoint-8/cloud/inventory-after-checkpoint-8.txt)) found no unattached volumes and no snapshots, one Elastic IP on the new instance, and the state bucket versioned, encrypted (AES256) and blocked from public access. Its tag query was refused: the deployment user's policy named the wrong action, fixed in Checkpoint 9. Destroying the whole demo is documented (`infra/README.md`) and has not been run; it is the owner's decision.

## Owed

Nothing for Checkpoint 8. The later cost reading belongs to Checkpoint 9; destroying the demo is the owner's decision.

## Known limitations

- **Metrics are lost when the instance is replaced.** Prometheus keeps 15 days, capped at 512 MB, on the instance's root disk; they are not valuable data. This happened in §6.
- **Each new instance installs the Docker that Amazon Linux offers that day.** The replacement got Docker 25.0.14, the first instance 25.0.16. Compose is pinned and checksum-verified, Docker is not.
- **Read termination protection back a minute after changing it.** AWS confirmed the change at once but reported the old value for about a minute.
- **The management endpoints have no authentication.** They are reachable only on the internal network or the loopback interface.
- **Locally, after editing an Nginx or monitoring file, restart that container.** The hash labels apply only to the deploy.
- **The work-queue gauges are read from the database at most every 5 seconds,** when Prometheus scrapes.
