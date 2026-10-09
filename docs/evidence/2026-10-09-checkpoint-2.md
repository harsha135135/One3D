# Evidence: Checkpoint 2: job, attempt, bed and QC

```text
Checkpoint / claim:  Roadmap Checkpoint 2. A confirmed order is released by an operator,
                     printed on one simulated printer with saved attempts, inspected
                     (whole-part QC) and marked ready for handoff. Failed attempts are kept,
                     the bed is never cleared automatically, and a failed QC blocks handoff.
Date:                2026-10-09
Code revision:       700256dcd89ed286bd7707ff6e2369a7d1741f1a (feat/checkpoint-2, on top of 283bd09).
                     The first run, the negative controls and the demonstrations ran on the
                     uncommitted working tree before this commit; the suite was rerun on the
                     commit itself (section 6)
Environment:         macOS (arm64); OpenJDK 21.0.12.1; Maven 3.9.13 via ./mvnw; Spring Boot
                     4.1.1; Testcontainers 2.0.5; PostgreSQL 17.11 (tests and local database)
Test runs:           first run 67 passed (13:23, before the attempt limit; section 1);
                     after the attempt limit 69 passed (13:49 working tree, 13:53 on 700256d;
                     section 6)
What is simulated:   Payment (demo-only shortcut, recorded as DEMO_SIMULATED) and the
                     printer: outcomes are reported through API calls. No real printer is
                     controlled. There is no separate simulator process yet (Checkpoint 3).
Owner explanation/change/debug review: PENDING (see the end of this record)
Related IMPLEMENTED.md rows: production release/jobs/simulator; failure recovery, bed-clear
                     and QC; Java modules/schema (all partial)
```

## 1. Automated checks: first run (67 tests, before the attempt limit)

**Command** (from `backend/`, Docker running):

```bash
./mvnw clean verify
```

**Actual result:** `BUILD SUCCESS`, exit code 0, total time 10.651 s, finished at 2026-10-09T13:23:35+05:30, on the working tree before the attempt limit was added. An earlier run at 13:20:30 had the same result. The run after the attempt limit (69 tests) is in [section 6](#6-attempt-limit-added-after-the-first-run).

The negative controls (section 3) and the local demonstrations (sections 4–5) ran just before one last change: `Printer.name` and `Printer.simulated` were marked `updatable = false`, after the SQL log showed them rewritten on every printer update. The full suite above was rerun after that change.

| Plugin | Class | Tests | New in CP2 |
|---|---|---|---|
| Surefire | `production.ProductionJobTest` (unit) | 10 | yes |
| Surefire | `orders.DemoPaymentDisabledTest` (web layer only) | 1 | yes |
| Surefire | `orders.CustomerOrderTest` (unit) | 3 | |
| Failsafe | `production.ProductionWorkflowIT` | 5 | yes |
| Failsafe | `production.ProductionTransitionsIT` | 13 | yes |
| Failsafe | `production.ProductionConstraintsIT` | 8 | yes |
| Failsafe | `SchemaMigrationIT` | 3 | +1 |
| Failsafe | `DatabaseConstraintsIT` | 7 | 1 changed |
| Failsafe | `orders.OrderApiIT` | 11 | |
| Failsafe | `catalog.CatalogApiIT`, `orders.OrderSnapshotIT`, `orders.OrderAtomicityIT` | 2 + 2 + 2 | |
| **Total** | | **67 passed** (14 unit, 53 integration), 0 failed, 0 skipped | |

The reports of this 67-test run are saved in [`2026-10-09-checkpoint-2/test-reports/`](2026-10-09-checkpoint-2/test-reports/).

**Checkpoint 1 tests changed by this checkpoint:**
- `SchemaMigrationIT` now expects migrations 1–4, and it checks the seeded simulated printer.
- `DatabaseConstraintsIT.orderStatusMustBeKnown` inserted status `SHIPPED` with no confirmation fields. Since V3, that row also breaks `customer_order_confirmation_matches_status`. PostgreSQL checks constraints in name order and reported that one first, so the test failed. The test row now carries confirmation fields, so it breaks only the status rule it is meant to test.

## 2. Rules, where they are enforced, and which test proves them

| Rule | Enforced by | Proven by |
|---|---|---|
| Placing or confirming an order never creates a job or starts a printer | No code path does it: release and attempts are separate operator endpoints | `ProductionWorkflowIT.confirmingAnOrderCreatesNoJobAndLeavesPrintersUntouched` |
| Only a `CONFIRMED` order can be released, once | `CustomerOrder.requireConfirmed`; `ProductionService.release`; DB `production_job_order_unique` | `ProductionTransitionsIT.anUnpaidOrderCannotBeReleased`, `anOrderIsReleasedOnlyOnce`; `ProductionConstraintsIT.anOrderHasAtMostOneJob` |
| Demo confirmation is labelled and can't be mistaken for payment | `ConfirmationSource.DEMO_SIMULATED`; DB `customer_order_confirmation_source_known`, `customer_order_confirmation_matches_status` | `ProductionWorkflowIT` (happy path); `ProductionConstraintsIT` (2 tests) |
| The demo shortcut exists only with the `demo` profile | `@ConditionalOnProperty` on `DemoPaymentController` | `DemoPaymentDisabledTest`; manual default-profile check (section 5) |
| An attempt needs an idle printer with a cleared bed | `Printer.reserveFor` | `ProductionJobTest.anOccupiedBedBlocksTheNextStartAndLeavesTheJobUnchanged`; `ProductionTransitionsIT.aBusyPrinterCannotStartAnotherJob` |
| Completion or failure leaves the bed occupied until an operator clears it | `Printer.releaseFrom` (bed unchanged); `Printer.clearBed` | `ProductionWorkflowIT.aCompletedPrintKeepsTheBedOccupiedUntilAnOperatorClearsIt`, `aFailedAttemptIsKeptAndARetryCreatesAttemptTwo` |
| A running attempt always occupies the bed | DB `printer_running_attempt_occupies_bed` | `ProductionConstraintsIT.aRunningAttemptMustOccupyTheBed` |
| A failure is kept with its reason; a retry is a new attempt number | `ProductionJob.failAttempt` and `startAttempt`; DB `print_attempt_failure_has_reason`, `print_attempt_number_unique` | `ProductionWorkflowIT.aFailedAttemptIsKeptAndARetryCreatesAttemptTwo`; `ProductionConstraintsIT` (2 tests) |
| Nothing retries automatically: each attempt is an operator call | `ProductionJob.startAttempt` is called only by `POST /api/ops/jobs/{id}/attempts` | the same retry test: the job stays `RELEASED` with 1 attempt until the operator starts attempt 2 |
| At most `MAX_ATTEMPTS = 3` attempts per job; print failures and QC failures both count; a 4th returns 409 `ATTEMPT_LIMIT_REACHED` and changes nothing (added after the first run, section 6) | `ProductionJob.startAttempt`, checked before the printer is reserved | `ProductionJobTest.aFourthAttemptIsRejectedAndChangesNothing`; `ProductionTransitionsIT.aJobHasAtMostThreeAttempts` |
| Only a completed print can be inspected; failed output never passes | `PrintAttempt.recordQc`; DB `print_attempt_qc_only_when_completed` | `ProductionTransitionsIT.aFailedAttemptCanNeverPassQc`, `aRunningAttemptCannotBeInspected`; `ProductionConstraintsIT.onlyACompletedAttemptCanHaveAQcResult` |
| QC is recorded once per attempt | `PrintAttempt.recordQc` | `ProductionTransitionsIT.anInspectedPartCannotBeInspectedAgain` |
| Ready for handoff only after the job's part passed QC | `ProductionJob.requireAccepted`, called by `ProductionService.markReadyForHandoff` | `ProductionWorkflowIT.aFailedQcBlocksHandoffUntilAReplacementPasses`; `ProductionTransitionsIT.anOrderIsNotReadyForHandoffBeforeItsJobIsAccepted` |
| V1 never controls a real printer | DB `printer_simulated_only` | `ProductionConstraintsIT.aPrinterMustBeSimulated` |

## 3. Negative controls (first run)

A passing test only shows something if it fails when the rule is missing. For each of two guards, the guard was removed temporarily, the related tests were run, and the file was restored byte-for-byte (checked with `cmp`). Both ran before the attempt limit was added. The limit's own control is in section 6.

| Experiment | Guard removed | Command | Result | What it demonstrates |
|---|---|---|---|---|
| 1a | `BED_NOT_CLEAR` check in `Printer.reserveFor` | `./mvnw verify -Dtest=ProductionJobTest -Dit.test='ProductionWorkflowIT,ProductionTransitionsIT'` | `BUILD FAILURE`: `ProductionJobTest.anOccupiedBedBlocksTheNextStartAndLeavesTheJobUnchanged` failed; Maven stopped before the integration tests | The unit test detects a print starting on an uncleared bed |
| 1b | Same guard, integration tests only | `./mvnw verify -Dtest=None -Dsurefire.failIfNoSpecifiedTests=false -Dit.test='ProductionWorkflowIT,ProductionTransitionsIT'` | `BUILD FAILURE`: `aCompletedPrintKeepsTheBedOccupiedUntilAnOperatorClearsIt` and `aFailedAttemptIsKeptAndARetryCreatesAttemptTwo`, both "Status expected:<409> but was:<201>" | Through the real API and database, the bed rule is what stops a new print after a completion or a failure. Nothing else in the code would catch it |
| 2 | `job.requireAccepted()` in `ProductionService.markReadyForHandoff` | same as 1b | `BUILD FAILURE`: `aFailedQcBlocksHandoffUntilAReplacementPasses` and `anOrderIsNotReadyForHandoffBeforeItsJobIsAccepted`, both "Status expected:<409> but was:<200>" | Without this one check, a part that failed QC, or was never inspected, could be marked ready for handoff. The order's own state check does not prevent it |

## 4. Local demonstration (`demo` profile)

This ran before the attempt limit was added. Its workflow used 2 attempts, so the limit would not have changed any step.

1. `docker compose up -d db` reused the local database from Checkpoint 1. It held three `AWAITING_PAYMENT` orders, with migrations 1 and 2 applied.
2. `./mvnw spring-boot:run -Dspring-boot.run.profiles=demo` logged `The following 1 profile is active: "demo"` and `Successfully applied 2 migrations to schema "public", now at version v4`. Existing orders were unaffected, and the app listened on `127.0.0.1:8080`.
3. The script [`demo-script.sh`](2026-10-09-checkpoint-2/demo/demo-script.sh) drove one order through the seeded printer `SIM-X1C-01 (simulated)`. The full transcript is in [`1-demo-profile-workflow.txt`](2026-10-09-checkpoint-2/demo/1-demo-profile-workflow.txt). Order `eaac7a42-…`, job `84776cc9-…`:

| Step | Result |
|---|---|
| Place order → simulate payment | 201 `AWAITING_PAYMENT` → 200 `CONFIRMED`, `confirmationSource: DEMO_SIMULATED` |
| Release | 201, job `RELEASED`, recipe revision `…0003`, no attempts |
| Start attempt 1 → simulate failure "First layer detached" | job `PRINTING` → `RELEASED`; attempt 1 `FAILED` with the reason |
| Printer after the failure | `bedState: OCCUPIED`, `currentAttemptId: null` |
| Start another attempt without clearing | **409 `BED_NOT_CLEAR`** |
| Clear bed → start attempt 2 → simulate completion | `CLEAR` → attempts `[1 FAILED, 2 RUNNING]` → job `AWAITING_QC`; bed `OCCUPIED` |
| Ready for handoff before QC | **409 `JOB_NOT_ACCEPTED`** |
| QC `PASSED` → ready for handoff → clear bed | job `ACCEPTED` → order `READY_FOR_HANDOFF` → bed `CLEAR` |

## 5. Default profile check

The assistant restarted the app without a profile (`./mvnw spring-boot:run`; `No active profile set`; `Schema "public" is up to date`). Transcript: [`2-default-profile-and-cp1-order.txt`](2026-10-09-checkpoint-2/demo/2-default-profile-and-cp1-order.txt).
- `POST /api/demo/orders/{id}/simulate-payment` returned **404**, and the order stayed `AWAITING_PAYMENT`.
- The Checkpoint 1 order `4af6c30c-…` still reads back, with the new fields `null` and its total unchanged.

## 6. Attempt limit added after the first run

**What changed:** `ProductionJob.MAX_ATTEMPTS = 3`. `startAttempt` rejects a 4th attempt with 409 `ATTEMPT_LIMIT_REACHED`. Every attempt counts, whether it failed to print or completed and then failed QC. The check runs after the job-status check and before the printer is reserved, so a rejected start leaves the job, its attempts and the printer unchanged.

**Who made it:** an assistant implemented this change. Harsha accepted it on 2026-10-09, subject to his final review. It is not Harsha's independent work and is not counted as his owner-review change.

**Files:** `ProductionJob.java` (constant and check), `ProductionJobTest.java` (`aFourthAttemptIsRejectedAndChangesNothing`) and `ProductionTransitionsIT.java` (`aJobHasAtMostThreeAttempts`). The integration test uses a print failure, a QC failure and another print failure, then clears the bed so that only the limit can block the 4th start.

**Test run:** `./mvnw clean verify` on the working tree with the limit: `BUILD SUCCESS`, 11.021 s, finished at 2026-10-09T13:49:03+05:30. **69 passed: 15 unit, 54 integration**, 0 failed, 0 skipped.

| Class | First run (67) | This run (69) |
|---|---|---|
| `production.ProductionJobTest` | 10 | 11 |
| `production.ProductionTransitionsIT` | 13 | 14 |
| All other classes | unchanged | unchanged |

Reports: [`2026-10-09-checkpoint-2/test-reports-69/`](2026-10-09-checkpoint-2/test-reports-69/).

**On the commit:** `./mvnw clean verify` on `700256d` (clean working tree under `backend/`): `BUILD SUCCESS`, 11.052 s, finished at 2026-10-09T13:53:07+05:30. 69 passed (15 unit, 54 integration), with the same counts per class as the 13:49 run.

**Negative control 3 (additional, run by the assistant during documentation):** the assistant removed the limit check from `startAttempt` and ran `./mvnw verify -Dtest=ProductionJobTest -Dit.test=ProductionTransitionsIT -Dmaven.test.failure.ignore=true`. Both new tests failed:
- `ProductionJobTest.aFourthAttemptIsRejectedAndChangesNothing`;
- `ProductionTransitionsIT.aJobHasAtMostThreeAttempts`, "Status expected:<409> but was:<201>".

The other 23 tests in those two classes passed. `maven.test.failure.ignore` made Maven report failures without stopping, which is why the exit code was 0. The file was restored byte-for-byte. This shows the two tests detect a missing limit.

## Known limitations

- **No protection against concurrent operator calls yet; Checkpoint 3 adds it.** Two simultaneous releases of one order would hit `production_job_order_unique`, and one caller would get a 500, not a 409. Two simultaneous starts on one printer are not prevented. Neither are two simultaneous starts for one job: both could read the same attempt count, and the database's `print_attempt_number_unique` would turn the second into a 500, so the attempt limit is not concurrency-safe either. Nothing in this checkpoint uses locks or version columns.
- The printer's outcomes come from API calls. The separate, restartable simulator with command IDs, a journal and uncertain-start handling is Checkpoint 3.
- The demo payment shortcut must be removed or confined further when test-mode payments arrive (Checkpoint 6).
- No authentication: `/api/ops` is a grouping, not access control, and the API listens on 127.0.0.1 only (Checkpoint 4).
- No customer progress timeline yet (Checkpoint 6). The facts it needs are stored: confirmation, release, attempt and QC timestamps.
- "Handed over" and customer cancellation are not implemented.
- **Exhausted jobs have no way forward.** A job that has used all 3 attempts stays `RELEASED`, with no operator override and no terminal "exhausted" or "cancelled" state, so its order cannot reach handoff. Deciding what happens then (refund, cancellation, manual override) is outside V1.

## Owner review (pending)

Harsha hasn't done this review yet. Use the rule table in section 2 to study. To complete it, without reference answers:

1. **Explain:**
   - Why do the order, the job and the attempt each have their own status?
   - What would go wrong with a single status field?
   - Trace `POST /api/ops/jobs/{id}/attempts`: which rows change, and in which transaction?
2. **Change a transition rule:** the proposed exercise (at most 3 attempts) was implemented by an assistant (section 6), so it does not count as Harsha's independent change. This item remains open. A replacement exercise is to be agreed with Harsha. For example: change `MAX_ATTEMPTS` to 2, update the affected tests, explain why each one changed, and run `./mvnw verify`.

Items 1 and 2 are the roadmap's Checkpoint 2 exercise. Neither is complete. Optional extra: run the demo script with the `demo` profile and SQL logging on (`--logging.level.org.hibernate.SQL=debug`), find the `update printer` issued when an attempt starts, and explain why it comes after `insert into print_attempt`.

Record the date and outcome here and in `IMPLEMENTED.md` only after Harsha has done these himself.
