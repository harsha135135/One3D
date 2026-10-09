# Evidence: Checkpoint 3: concurrency and durable simulator

```text
Checkpoint / claim:  Roadmap Checkpoint 3. Start commands go to a separate, restartable
                     printer simulator that journals every command in SQLite before it
                     answers. Lost replies, restarts and late workers are reconciled
                     without a second execution. Competing operators get exactly one
                     owner, and races return 409. Bed clearances name the print they clear.
Date:                2026-10-09
Code revision:       45c9103 (simulator), 45f9791 (backend), 7f34bef (Compose and CI) on
                     feat/checkpoint-3, on top of main 5282d54
Environment:         macOS (arm64); OpenJDK 21.0.12.1; Maven 3.9.13 via ./mvnw; Spring Boot
                     4.1.1; Testcontainers 2.0.5; PostgreSQL 17.11; simulator image built from
                     simulator/Dockerfile (eclipse-temurin 21); Docker engine 29.6.2
Implemented by:      the assistant, under the owner's instruction of 2026-10-09
                     (see IMPLEMENTED.md, "Process change")
What is simulated:   The printer (a separate process; no hardware) and payment (demo-only
                     shortcut). No real printer is controlled.
Owner review:        deferred by the owner until V1 is complete
```

## 1. Automated checks

Both suites are run from their own directory:

```bash
cd simulator && ./mvnw clean verify     # 18:55:51 IST, BUILD SUCCESS, 4.8 s
cd backend   && ./mvnw clean verify     # 18:56:08 IST, BUILD SUCCESS, 16.0 s
```

| Suite | Class | Tests | Notes |
|---|---|---|---|
| simulator | `PrinterSimulatorTest` | 9 | Rules against a real SQLite journal, including a restart on the same file |
| simulator | `HttpApiTest` | 5 | HTTP API. A dropped reply makes the caller fail with an `IOException` in under 2 s; a restarted process answers from the same journal |
| backend (unit) | `ProductionJobTest` 15, `CustomerOrderTest` 3, `DemoPaymentDisabledTest` 1 | 19 | |
| backend (integration) | `DispatchReconciliationIT` | 10 | New |
| | `ConcurrencyIT` | 10 | New; two tests, each repeated 5 times |
| | `BedClearanceIT` | 3 | New |
| | `ProductionConstraintsIT` | 13 | +5 for V5 |
| | `ProductionWorkflowIT` 5, `ProductionTransitionsIT` 14 | 19 | Rewritten to use the real simulator |
| | CP1 classes (`OrderApiIT` 15, `DatabaseConstraintsIT` 7, `SchemaMigrationIT` 3, `CatalogApiIT` 2, `OrderSnapshotIT` 2, `OrderAtomicityIT` 2) | 31 | `SchemaMigrationIT` now expects migrations 1–5 |
| **Total** | | **119 passed**: simulator 14; backend 105 (19 unit, 86 integration). 0 failed, 0 skipped | |

The backend integration tests start PostgreSQL and the simulator as separate containers. The simulator image is built from `simulator/Dockerfile`, with its journal in a temporary host directory. The background worker is switched off in tests, which call `DispatchWorker.runOnce()` and `ExecutionObserver.observeOnce()` themselves.

The first image build took about 80 s locally; later builds reuse the Docker layer cache. Reports are in [`backend-test-reports/`](2026-10-09-checkpoint-3/backend-test-reports/) and [`simulator-test-reports/`](2026-10-09-checkpoint-3/simulator-test-reports/).

CI (`.github/workflows/ci.yml`) runs both suites on every pull request and on every push to `main`. Its first run, on `7f34bef` ([run 37936876770](https://github.com/harsha135135/One3D/actions/runs/37936876770)), passed both jobs:
- simulator: 14 tests, Maven 17 s;
- backend: 19 unit and 86 integration tests, Maven 2:00 min, job 2 min 16 s. That includes building the simulator image on a runner with no layer cache.

Caching that build is left for Checkpoint 7.

## 2. Rules, enforcement and tests

### New in Checkpoint 3

| Rule | Enforced by | Proven by |
|---|---|---|
| A start is recorded before the printer is told; the printer is told only by the worker, outside any database transaction | `ProductionService.startAttempt` (one transaction: attempt `START_INTENT`, command, work row); `DispatchWorker` | `DispatchReconciliationIT.aStartIsRecordedFirstAndConfirmedByTheSimulatorJournal` |
| The simulator journals a command before answering; the same id returns the same decision; the same id with another payload is refused | `PrinterSimulator.start` (one SQLite transaction); `execution` primary key on `attempt_id` | `PrinterSimulatorTest` (repeat, mismatch, restart); `DispatchReconciliationIT.aRepeatedDeliveryStartsThePrintOnlyOnce` |
| A lost reply or unreachable simulator never frees the printer or creates a new attempt. The attempt stays `RECONCILIATION_REQUIRED` until the journal answers | `DispatchOutcomes.apply` (UNKNOWN keeps ownership); the worker asks `GET /commands/{id}` before sending | `DispatchReconciliationIT.aLostAcknowledgementHoldsThePrinterUntilTheJournalIsChecked` and `anUnreachableSimulatorExhaustsVisiblyAndAnOperatorCanReconcileLater`; negative control E |
| Delivery is retried a bounded number of times; exhausted work is shown to operators and retried only on request | `PendingWorkStore.retryLaterIfStillClaimed` (`max_attempts`, backoff); `GET /api/ops/work`; `POST /api/ops/attempts/{id}/reconcile` | `anUnreachableSimulatorExhaustsVisiblyAndAnOperatorCanReconcileLater` |
| A worker that crashed loses its claim when the lease expires; another worker takes over | `PendingWorkStore.claim` (`FOR UPDATE SKIP LOCKED`, lease, new token) | `workClaimedByACrashedWorkerIsRecoveredAfterItsLeaseExpires` |
| A late worker cannot overwrite a newer result, even while the replacement still holds the claim | Every result update requires `claim_token`; the token check runs first, and zero updated rows means no domain change | `aLateWorkerCannotWriteWhileTheReplacementWorkerHoldsTheClaim`, `aLateWorkerCannotOverwriteTheResultOfTheWorkerThatReplacedIt`; negative control B |
| The simulator survives a restart; a running print and its completion are not lost | SQLite journal on a volume; observer polls by command id | `PrinterSimulatorTest.stateSurvivesARestartOnTheSameJournal`; `HttpApiTest.aRestartedProcessAnswersFromTheSameJournal`; `DispatchReconciliationIT.aSimulatorRestartKeepsTheRunningPrintAndItsCompletionIsObserved`; demo step 2 |
| A start the printer refuses (busy, or occupied bed) is undone exactly: printer free, bed CLEAR, clearance unused again, job `RELEASED` | `ProductionJob.rejectBeforeStart`, `Printer.undoReservation`, `BedClearance.release` | `aStartRefusedByAnOccupiedBedIsUndoneExactly`, `aStartRefusedByABusyPrinterReturnsTheClearanceItUsed` |
| A refused start does not count toward the 3-attempt limit | `PrintAttempt.countsTowardLimit` | `ProductionJobTest.aRefusedStartIsUndoneAndDoesNotCountTowardTheLimit` |
| Two operators starting prints on one printer at the same moment: exactly one wins, the other gets 409 | Three independent layers: `PrinterRepository.findForUpdate` (`SELECT … FOR UPDATE`), partial unique index `print_attempt_one_active_per_printer`, `@Version` on `Printer` | `ConcurrencyIT.twoSimultaneousStartsOnOnePrinterGiveExactlyOneAttempt` (5 repetitions); negative controls A1–A5 |
| Two simultaneous releases of one order give one job; the loser gets 409 `ALREADY_RELEASED`, not 500 | `production_job_order_unique`, mapped by `ConstraintConflicts` | `ConcurrencyIT.twoSimultaneousReleasesOfOneOrderGiveExactlyOneJob` (5 repetitions) |
| A clearance must name the print on the bed; a delayed one for an earlier print is refused, and a repeated one gets 409 | `Printer.clearBed` (`STALE_CLEARANCE`, `BED_ALREADY_CLEAR`); DB `bed_clearance_cleared_attempt_unique`, `bed_clearance_one_open_per_printer` | `BedClearanceIT` (3 tests); `ProductionJobTest.aClearanceMustNameThePrintOnTheBed`; negative control C |
| The simulator also refuses a start whose clearance names an earlier print | `PrinterSimulator.start` (clearance must equal `bed_cycle_attempt_id`) | `PrinterSimulatorTest.aClearanceForAnEarlierPrintCannotClearALaterOne`; `BedClearanceIT.theSimulatorAlsoRefusesAStartCarryingAClearanceForAnEarlierPrint` |
| Database rules added in V5 | `print_attempt_one_active_per_printer` and `print_attempt_one_active_per_job`, `printer_bed_cycle_matches_state`, `bed_clearance_one_open_per_printer`, `pending_work_claim_matches_status` | `ProductionConstraintsIT` (5 new tests) |

### Checkpoint 2 rules on the new code

| Checkpoint 2 rule | Test now |
|---|---|
| Placing or confirming an order starts nothing | `ProductionWorkflowIT.confirmingAnOrderCreatesNoJobAndSendsNothingToAPrinter` also checks that no command exists and the simulator has never seen the printer |
| A finished or failed print leaves the bed occupied until cleared | `ProductionWorkflowIT.aCompletedPrintKeepsTheBedOccupiedUntilAnOperatorClearsIt`, `aFailedAttemptIsKeptAndARetryCreatesAttemptTwo`; negative control D1 |
| A failure is kept; a retry is a new attempt | `ProductionWorkflowIT.aFailedAttemptIsKeptAndARetryCreatesAttemptTwo` |
| QC must pass before handoff | `ProductionWorkflowIT.aFailedQcBlocksHandoffUntilAReplacementPasses`, `ProductionTransitionsIT.anOrderIsNotReadyForHandoffBeforeItsJobIsAccepted`; negative control D2 |
| At most 3 attempts per job | `ProductionTransitionsIT.aJobHasAtMostThreeAttempts`; `ProductionJobTest.aFourthCountedAttemptIsRejectedAndChangesNothing` |
| The demo payment shortcut exists only with the `demo` profile | `DemoPaymentDisabledTest` (unchanged) |
| An ended attempt cannot end again | `ProductionTransitionsIT.anEndedAttemptCannotEndAgain`: the simulator answers 409 `NOT_RUNNING` and the observer changes nothing |

## 3. Negative controls

Each guard was removed temporarily, the related tests were run, and the files were restored and checked with `cmp`. Outputs are in [`negative-controls/`](2026-10-09-checkpoint-3/negative-controls/).

| # | Removed | Result | What it shows |
|---|---|---|---|
| A3 | Printer row lock, both partial unique indexes and `@Version` on `Printer` | `ConcurrencyIT` start race failed in all 5 repetitions (two 201s) | The race is real and the test detects it |
| A4 | Index and version (row lock kept) | 10/10 passed | The row lock alone prevents a second owner |
| A5 | Lock and version (index kept) | 10/10 passed | The partial unique index alone prevents it (loser gets 409 via the constraint mapping) |
| A1 | Lock only (index and version kept) | 10/10 passed | Defence in depth: the remaining layers still hold |
| B | `claim_token` condition in both result updates | `aLateWorkerCannotWriteWhileTheReplacementWorkerHoldsTheClaim` failed | Only the token separates two workers that both see CLAIMED. The other late-worker test still passed, because the replacement had already marked the row DONE |
| C | The `STALE_CLEARANCE` check in `Printer.clearBed` | `ProductionJobTest.aClearanceMustNameThePrintOnTheBed` and `BedClearanceIT.aDelayedClearance…` failed | In the integration test the database's `bed_clearance_cleared_attempt_unique` still rejected the write and rolled it back, but with the misleading code `BED_ALREADY_CLEAR`. The domain check gives the correct code |
| D1 | `BED_NOT_CLEAR` in `Printer.reserveFor` (CP2 guard) | 1 unit and 3 integration tests failed (201 instead of 409) | The CP2 bed rule is still covered |
| D2 | `job.requireAccepted()` before handoff (CP2 guard) | 2 integration tests failed (200 instead of 409) | The CP2 QC-before-handoff rule is still covered |
| E | Treating an unknown start outcome as a refusal, which frees the printer | The lost-acknowledgement and unreachable-simulator tests failed | "A timeout is not permission to reassign" is tested |

## 4. Local demonstration

The setup reused your local database and checked that V5 applies over it:
- `docker compose up -d --build db simulator` started PostgreSQL with the existing local data (migrations 1–4, 5 orders, two CP2 attempts without commands) and the simulator.
- `./mvnw spring-boot:run -Dspring-boot.run.profiles=demo` ran with the background worker on and logged `Successfully applied 1 migration … now at version v5`.
- The CP2 attempts have no command, so the observer and worker ignore them.

[`demo-script.sh`](2026-10-09-checkpoint-3/demo/demo-script.sh) then ran against the seeded printer. Full output is in [`1-local-workflow.txt`](2026-10-09-checkpoint-3/demo/1-local-workflow.txt):

| Step | Observed |
|---|---|
| 1. Start | 201 with the attempt `START_INTENT`; 2 s later `RUNNING`; simulator shows it running |
| 2. `docker compose restart simulator` while printing | Same running attempt in the journal after the restart; backend still `RUNNING` (the observer logged "Could not observe" during the restart) |
| 3. Completion by the simulator dev control | Job `AWAITING_QC`; printer free, bed `OCCUPIED`, `bedCycleAttemptId` = that attempt |
| 4. QC passed; clear naming that attempt | `ACCEPTED`; bed `CLEAR` |
| 5. Reply dropped | Statuses observed: `START_INTENT` → `RECONCILIATION_REQUIRED` (another job's start meanwhile: 409 `PRINTER_BUSY`) → `RUNNING`; the journal shows one `STARTED` command; the job has one attempt |
| 6. Failure, then a clearance naming the earlier print | 409 `STALE_CLEARANCE`; naming the current print clears the bed |
| 7. Two simultaneous `curl` starts for two jobs | One 201, one 409; one active attempt on the printer |

## 5. Design notes

- **Clearance travels with the start command.** The command carries `clearedAttemptId`, and the simulator consumes it in the same transaction that starts the print. There is no separate clearance message that could arrive out of order.
- **One printer, keyed by id.** The simulator keys its virtual printers by printer id. The V1 demonstration uses one printer, `SIM-X1C-01`. The keying exists only so tests can share one simulator process without affecting each other; it is not a multi-printer feature.
- **Query before send.** The worker asks the journal before sending, so a retry after a lost reply never sends a second start.
- **Lock order.** Every transaction that changes a printer locks its row first, so concurrent changes to one printer are serialized.

## Known limitations

- The simulator's `/dev` controls (complete, fail, drop the next reply) exist only when `SIMULATOR_DEV_CONTROLS=true`. Compose sets this for local development, with the port on 127.0.0.1. A deployment must never enable or expose them (Checkpoint 7).
- No authentication yet (Checkpoint 4). `/api/ops` is a grouping; the API listens on 127.0.0.1.
- Processing is at-least-once: a start command can be delivered more than once, and the simulator answers repeats from its journal.
- A simulator that loses its journal (volume deleted) disagrees with the backend about running prints and bed state. Recovery after a database restore is a CP8 runbook item.
- Exhausted delivery work needs an operator. The operator view is the API until the CP4 frontend.
- The observer polls once per second. No push notifications.
- Jobs that have used all 3 attempts still have no override or terminal state (unchanged from CP2).
- The demo payment shortcut is still confined to the `demo` profile (Checkpoint 6 replaces it).
