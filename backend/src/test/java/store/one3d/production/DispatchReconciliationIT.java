package store.one3d.production;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import store.one3d.background.PendingWorkStore;
import store.one3d.background.WorkKind;
import store.one3d.support.ApiSteps;
import store.one3d.support.PostgresIntegrationTest;
import store.one3d.support.ProductionFixtures;
import store.one3d.support.SimulatorRig;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Delivering start commands to the simulator process and reconciling what is
 * uncertain: lost replies, an unreachable or restarted simulator, crashed and
 * late workers, and starts the printer refuses.
 */
@PostgresIntegrationTest
class DispatchReconciliationIT {

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    DispatchWorker worker;

    @Autowired
    ExecutionObserver observer;

    @Autowired
    DispatchOutcomes outcomes;

    @Autowired
    PendingWorkStore workStore;

    @Autowired
    SimulatorRig simulator;

    private ApiSteps api;
    private String printerId;

    @BeforeEach
    void setUp() {
        api = new ApiSteps(mvc, worker, observer, simulator);
        printerId = new ProductionFixtures(jdbc).clearPrinter().toString();
    }

    @AfterEach
    void reachableAgain() {
        simulator.setUnreachable(false);
    }

    @Test
    void aStartIsRecordedFirstAndConfirmedByTheSimulatorJournal() throws Exception {
        String attemptId = api.recordStart(api.releasedJob(), printerId);
        assertThat(attemptStatus(attemptId)).isEqualTo("START_INTENT");
        assertThat(commandOf(attemptId)).containsEntry("status", "PENDING");
        assertThat(simulator.printer(printerId)).startsWith("404"); // nothing sent yet

        api.dispatch();

        assertThat(attemptStatus(attemptId)).isEqualTo("RUNNING");
        assertThat(commandOf(attemptId)).containsEntry("status", "ACKNOWLEDGED");
        assertThat(workOf(attemptId)).containsEntry("status", "DONE");
        assertThat(simulator.printer(printerId)).contains("\"runningAttemptId\":\"" + attemptId + "\"");
    }

    @Test
    void aLostAcknowledgementHoldsThePrinterUntilTheJournalIsChecked() throws Exception {
        simulator.dropNextStartAcknowledgement(printerId);
        String jobId = api.releasedJob();
        String attemptId = api.recordStart(jobId, printerId);

        api.dispatch(); // the simulator starts the print, but its reply is lost

        assertThat(attemptStatus(attemptId)).isEqualTo("RECONCILIATION_REQUIRED");
        assertThat(commandOf(attemptId)).containsEntry("status", "UNCERTAIN");
        assertThat(workOf(attemptId)).containsEntry("status", "PENDING");
        // The uncertain attempt keeps the printer and its job: nothing else can start.
        api.startAttemptRequest(api.releasedJob(), printerId)
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("PRINTER_BUSY"));
        api.get("/api/ops/jobs/" + jobId).andExpect(jsonPath("$.status").value("PRINTING"));

        api.dispatch(); // asks the journal by command id instead of starting again

        assertThat(attemptStatus(attemptId)).isEqualTo("RUNNING");
        assertThat(commandOf(attemptId)).containsEntry("status", "ACKNOWLEDGED");
        assertThat(attemptsOfJob(jobId)).hasSize(1);
        String command = simulator.command(commandOf(attemptId).get("id").toString());
        assertThat(command).contains("\"outcome\":\"STARTED\"").contains("\"phase\":\"RUNNING\"");
    }

    @Test
    void aRepeatedDeliveryStartsThePrintOnlyOnce() throws Exception {
        String attemptId = api.startAttempt(api.releasedJob(), printerId);
        String commandId = commandOf(attemptId).get("id").toString();

        // Deliver the same command again, as a retry after a crash would.
        jdbc.sql("UPDATE pending_work SET status = 'PENDING', available_at = now() WHERE command_id = ?::uuid")
                .param(commandId).update();
        api.dispatch();
        String direct = simulator.sendStart(commandId, printerId, attemptId, null);

        assertThat(attemptStatus(attemptId)).isEqualTo("RUNNING");
        // The direct resend used a different payload hash, so the simulator refuses it.
        assertThat(direct).startsWith("409").contains("PAYLOAD_MISMATCH");
        assertThat(simulator.printer(printerId)).contains("\"runningAttemptId\":\"" + attemptId + "\"");
        simulator.complete(printerId);
        assertThat(simulator.complete(printerId)).startsWith("409"); // there was only one execution
    }

    @Test
    void anUnreachableSimulatorExhaustsVisiblyAndAnOperatorCanReconcileLater() throws Exception {
        simulator.setUnreachable(true);
        String jobId = api.releasedJob();
        String attemptId = api.recordStart(jobId, printerId);

        for (int i = 0; i < 5; i++) {
            api.dispatch();
        }

        assertThat(attemptStatus(attemptId)).isEqualTo("RECONCILIATION_REQUIRED");
        assertThat(workOf(attemptId)).containsEntry("status", "EXHAUSTED").containsEntry("attempts", 5);
        String commandId = commandOf(attemptId).get("id").toString();
        api.get("/api/ops/work?status=EXHAUSTED")
                .andExpect(jsonPath("$[?(@.commandId == '" + commandId + "')].lastError").isNotEmpty());
        api.dispatch(); // exhausted work is not retried automatically
        assertThat(workOf(attemptId)).containsEntry("attempts", 5);

        simulator.setUnreachable(false);
        api.post("/api/ops/attempts/" + attemptId + "/reconcile").andExpect(status().isOk());
        api.dispatch(); // journal says "never received", so the same command is sent now

        assertThat(attemptStatus(attemptId)).isEqualTo("RUNNING");
        assertThat(attemptsOfJob(jobId)).hasSize(1);
    }

    @Test
    void aSimulatorRestartKeepsTheRunningPrintAndItsCompletionIsObserved() throws Exception {
        String jobId = api.releasedJob();
        String attemptId = api.startAttempt(jobId, printerId);

        simulator.restart(); // new process, same journal file, new port
        api.observe();
        assertThat(attemptStatus(attemptId)).isEqualTo("RUNNING");
        assertThat(simulator.printer(printerId)).contains("\"runningAttemptId\":\"" + attemptId + "\"");

        simulator.complete(printerId);
        api.observe();

        assertThat(attemptStatus(attemptId)).isEqualTo("COMPLETED");
        api.get("/api/ops/jobs/" + jobId).andExpect(jsonPath("$.status").value("AWAITING_QC"));
    }

    @Test
    void workClaimedByACrashedWorkerIsRecoveredAfterItsLeaseExpires() throws Exception {
        String attemptId = api.recordStart(api.releasedJob(), printerId);
        PendingWorkStore.ClaimedWork abandoned = claimOnly(attemptId);
        expireLease(abandoned);

        api.dispatch(); // a new worker, as after a backend restart

        assertThat(attemptStatus(attemptId)).isEqualTo("RUNNING");
        assertThat(workOf(attemptId)).containsEntry("status", "DONE").containsEntry("attempts", 2);
    }

    @Test
    void aLateWorkerCannotOverwriteTheResultOfTheWorkerThatReplacedIt() throws Exception {
        String attemptId = api.recordStart(api.releasedJob(), printerId);
        PendingWorkStore.ClaimedWork late = claimOnly(attemptId);
        expireLease(late);
        api.dispatch(); // the replacement worker confirms the start

        boolean applied = outcomes.apply(late, DispatchResult.unknown("timeout reported after the lease expired"));

        assertThat(applied).isFalse();
        assertThat(attemptStatus(attemptId)).isEqualTo("RUNNING");
        assertThat(commandOf(attemptId)).containsEntry("status", "ACKNOWLEDGED");
        assertThat(workOf(attemptId)).containsEntry("status", "DONE");
    }

    @Test
    void aLateWorkerCannotWriteWhileTheReplacementWorkerHoldsTheClaim() throws Exception {
        String attemptId = api.recordStart(api.releasedJob(), printerId);
        PendingWorkStore.ClaimedWork late = claimOnly(attemptId);
        expireLease(late);
        PendingWorkStore.ClaimedWork replacement = claimOnly(attemptId); // reclaimed, still in progress

        // Only the claim token tells these two apart: both rows say CLAIMED.
        assertThat(outcomes.apply(late, DispatchResult.unknown("late timeout"))).isFalse();
        assertThat(attemptStatus(attemptId)).isEqualTo("START_INTENT");
        assertThat(workOf(attemptId)).containsEntry("status", "CLAIMED");

        assertThat(outcomes.apply(replacement, new DispatchResult(DispatchResult.Kind.STARTED, null))).isTrue();
        assertThat(attemptStatus(attemptId)).isEqualTo("RUNNING");
    }

    @Test
    void aStartRefusedByAnOccupiedBedIsUndoneExactly() throws Exception {
        // Something the backend does not know about is on the simulator's bed.
        assertThat(simulator.sendStart(UUID.randomUUID().toString(), printerId, "external-" + UUID.randomUUID(), null))
                .contains("\"outcome\":\"STARTED\"");
        assertThat(simulator.complete(printerId)).startsWith("200");
        String jobId = api.releasedJob();
        Map<String, Object> printerBefore = printerRow();

        String attemptId = api.startAttempt(jobId, printerId);

        assertThat(attemptStatus(attemptId)).isEqualTo("REJECTED_BEFORE_START");
        assertThat(attempt(attemptId).get("failure_reason").toString()).contains("REJECTED_BED_NOT_CLEAR");
        assertThat(commandOf(attemptId)).containsEntry("status", "REJECTED");
        assertThat(printerRow()).isEqualTo(printerBefore);
        api.get("/api/ops/jobs/" + jobId).andExpect(jsonPath("$.status").value("RELEASED"));
    }

    @Test
    void aStartRefusedByABusyPrinterReturnsTheClearanceItUsed() throws Exception {
        String firstJob = api.releasedJob();
        String first = api.startAttempt(firstJob, printerId);
        api.complete(first);
        api.clearBed(printerId);
        // Another print starts on the simulator, using the operator's clearance first.
        assertThat(simulator.sendStart(UUID.randomUUID().toString(), printerId, "external-" + UUID.randomUUID(), first))
                .contains("\"outcome\":\"STARTED\"");
        String secondJob = api.releasedJob();
        Map<String, Object> printerBefore = printerRow();

        String refused = api.startAttempt(secondJob, printerId);

        assertThat(attemptStatus(refused)).isEqualTo("REJECTED_BEFORE_START");
        assertThat(attempt(refused).get("failure_reason").toString()).contains("REJECTED_BUSY");
        assertThat(printerRow()).isEqualTo(printerBefore);
        Map<String, Object> clearance = jdbc.sql(
                        "SELECT cleared_attempt_id, consumed_by_attempt_id FROM bed_clearance WHERE cleared_attempt_id = ?::uuid")
                .param(first).query().singleRow();
        assertThat(clearance.get("consumed_by_attempt_id")).isNull();
        api.get("/api/ops/jobs/" + secondJob).andExpect(jsonPath("$.status").value("RELEASED"));
    }

    // --- helpers -------------------------------------------------------------

    private PendingWorkStore.ClaimedWork claimOnly(String attemptId) {
        UUID commandId = (UUID) commandOf(attemptId).get("id");
        return workStore.claim(WorkKind.DISPATCH_START, 50, Duration.ofSeconds(30)).stream()
                .filter(work -> work.subjectId().equals(commandId))
                .findFirst()
                .orElseThrow();
    }

    private void expireLease(PendingWorkStore.ClaimedWork work) {
        jdbc.sql("UPDATE pending_work SET claimed_until = now() - interval '1 second' WHERE id = ?")
                .param(work.id()).update();
    }

    private String attemptStatus(String attemptId) {
        return attempt(attemptId).get("status").toString();
    }

    private Map<String, Object> attempt(String attemptId) {
        return jdbc.sql("SELECT status, failure_reason FROM print_attempt WHERE id = ?::uuid")
                .param(attemptId).query().singleRow();
    }

    private List<String> attemptsOfJob(String jobId) throws Exception {
        return JsonPath.read(api.jobJson(jobId), "$.attempts[*].id");
    }

    private Map<String, Object> commandOf(String attemptId) {
        return jdbc.sql("SELECT id, status FROM printer_command WHERE attempt_id = ?::uuid")
                .param(attemptId).query().singleRow();
    }

    private Map<String, Object> workOf(String attemptId) {
        return jdbc.sql("""
                        SELECT w.status, w.attempts FROM pending_work w
                        JOIN printer_command c ON c.id = w.command_id WHERE c.attempt_id = ?::uuid
                        """)
                .param(attemptId).query().singleRow();
    }

    private Map<String, Object> printerRow() {
        return jdbc.sql("SELECT bed_state, bed_cycle_attempt_id, current_attempt_id FROM printer WHERE id = ?::uuid")
                .param(printerId).query().singleRow();
    }
}
