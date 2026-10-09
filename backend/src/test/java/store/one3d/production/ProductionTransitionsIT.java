package store.one3d.production;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import store.one3d.support.ApiSteps;
import store.one3d.support.PostgresIntegrationTest;
import store.one3d.support.ProductionFixtures;
import store.one3d.support.SimulatorRig;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Every rejected transition returns 409 with a specific code and changes nothing. */
@PostgresIntegrationTest
class ProductionTransitionsIT {

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    DispatchWorker worker;

    @Autowired
    ExecutionObserver observer;

    @Autowired
    SimulatorRig simulator;

    private ApiSteps api;
    private ProductionFixtures fixtures;
    private String printerId;

    @BeforeEach
    void setUp() {
        api = new ApiSteps(mvc, worker, observer, simulator);
        fixtures = new ProductionFixtures(jdbc);
        printerId = fixtures.clearPrinter().toString();
    }

    @Test
    void anUnpaidOrderCannotBeReleased() throws Exception {
        String orderId = api.placeOrder();

        expectConflict(api.post("/api/ops/orders/" + orderId + "/release"), "ORDER_NOT_CONFIRMED");
    }

    @Test
    void anOrderIsReleasedOnlyOnce() throws Exception {
        String orderId = api.placeConfirmedOrder();
        api.release(orderId);

        expectConflict(api.post("/api/ops/orders/" + orderId + "/release"), "ALREADY_RELEASED");
    }

    @Test
    void paymentCanBeSimulatedOnlyOnce() throws Exception {
        String orderId = api.placeConfirmedOrder();

        expectConflict(api.post("/api/demo/orders/" + orderId + "/simulate-payment"), "ORDER_NOT_AWAITING_PAYMENT");
    }

    @Test
    void aBusyPrinterCannotStartAnotherJob() throws Exception {
        String firstJob = api.releasedJob();
        String secondJob = api.releasedJob();
        api.startAttempt(firstJob, printerId);

        expectConflict(api.startAttemptRequest(secondJob, printerId), "PRINTER_BUSY");
        api.get("/api/ops/jobs/" + secondJob)
                .andExpect(jsonPath("$.status").value("RELEASED"))
                .andExpect(jsonPath("$.attempts", hasSize(0)));
    }

    @Test
    void aPrintingJobCannotStartASecondAttempt() throws Exception {
        String jobId = api.releasedJob();
        api.startAttempt(jobId, printerId);
        String otherPrinter = fixtures.clearPrinter().toString();

        expectConflict(api.startAttemptRequest(jobId, otherPrinter), "JOB_NOT_RELEASED");
    }

    @Test
    void aJobHasAtMostThreeAttempts() throws Exception {
        String jobId = api.releasedJob();
        // Attempts 1–3 all start, whether the previous one failed to print or failed QC.
        api.fail(api.startAttempt(jobId, printerId), "Spaghetti");
        api.clearBed(printerId);
        String rejectedPart = api.startAttempt(jobId, printerId);
        api.complete(rejectedPart);
        api.qc(rejectedPart, "FAILED").andExpect(status().isOk());
        api.clearBed(printerId);
        api.fail(api.startAttempt(jobId, printerId), "Nozzle clog");
        // With the bed cleared, only the attempt limit can stop a 4th start.
        api.clearBed(printerId);
        String jobBefore = jobBody(jobId);
        Map<String, Object> printerBefore = printerRow();

        expectConflict(api.startAttemptRequest(jobId, printerId), "ATTEMPT_LIMIT_REACHED");

        assertThat(jobBody(jobId)).isEqualTo(jobBefore);
        api.get("/api/ops/jobs/" + jobId)
                .andExpect(jsonPath("$.status").value("RELEASED"))
                .andExpect(jsonPath("$.attempts[*].attemptNumber", contains(1, 2, 3)));
        assertThat(jdbc.sql("SELECT count(*) FROM print_attempt WHERE job_id = ?::uuid")
                .param(jobId).query(Long.class).single()).isEqualTo(3L);
        assertThat(printerRow()).isEqualTo(printerBefore)
                .containsEntry("bed_state", "CLEAR")
                .containsEntry("current_attempt_id", null);
    }

    @Test
    void anEndedAttemptCannotEndAgain() throws Exception {
        String jobId = api.releasedJob();
        String attemptId = api.startAttempt(jobId, printerId);
        api.complete(attemptId);

        // The printer has nothing running any more, and observing again changes nothing.
        assertThat(simulator.fail(printerId, "too late")).startsWith("409").contains("NOT_RUNNING");
        api.observe();
        api.get("/api/ops/jobs/" + jobId)
                .andExpect(jsonPath("$.status").value("AWAITING_QC"))
                .andExpect(jsonPath("$.attempts[0].status").value("COMPLETED"));
    }

    @Test
    void aRunningAttemptCannotBeInspected() throws Exception {
        String attemptId = api.startAttempt(api.releasedJob(), printerId);

        expectConflict(api.qc(attemptId, "PASSED"), "JOB_NOT_AWAITING_QC");
    }

    @Test
    void aFailedAttemptCanNeverPassQc() throws Exception {
        String jobId = api.releasedJob();
        String failed = api.startAttempt(jobId, printerId);
        api.fail(failed, "Spaghetti");
        api.clearBed(printerId);
        api.complete(api.startAttempt(jobId, printerId)); // job is now awaiting QC

        expectConflict(api.qc(failed, "PASSED"), "ATTEMPT_NOT_COMPLETED");
        api.get("/api/ops/jobs/" + jobId).andExpect(jsonPath("$.status").value("AWAITING_QC"));
    }

    @Test
    void anInspectedPartCannotBeInspectedAgain() throws Exception {
        String jobId = api.releasedJob();
        String rejected = api.startAttempt(jobId, printerId);
        api.complete(rejected);
        api.qc(rejected, "FAILED").andExpect(status().isOk());
        api.clearBed(printerId);
        api.complete(api.startAttempt(jobId, printerId)); // job is awaiting QC again

        expectConflict(api.qc(rejected, "PASSED"), "QC_ALREADY_RECORDED");
    }

    @Test
    void theBedCannotBeClearedWhilePrintingOrWhenAlreadyClear() throws Exception {
        expectConflict(api.clearBedRequest(printerId, UUID.randomUUID().toString()), "BED_ALREADY_CLEAR");

        String running = api.startAttempt(api.releasedJob(), printerId);

        expectConflict(api.clearBedRequest(printerId, running), "PRINTER_BUSY");
    }

    @Test
    void anOrderIsNotReadyForHandoffBeforeItsJobIsAccepted() throws Exception {
        String orderId = api.placeConfirmedOrder();
        expectConflict(api.post("/api/ops/orders/" + orderId + "/ready-for-handoff"), "JOB_NOT_ACCEPTED");

        String jobId = api.release(orderId);
        api.complete(api.startAttempt(jobId, printerId)); // awaiting QC, not accepted

        expectConflict(api.post("/api/ops/orders/" + orderId + "/ready-for-handoff"), "JOB_NOT_ACCEPTED");
        api.get("/api/orders/" + orderId).andExpect(jsonPath("$.status").value("CONFIRMED"));
    }

    @Test
    void unknownRecordsAreNotFound() throws Exception {
        UUID unknown = UUID.randomUUID();

        api.post("/api/ops/orders/" + unknown + "/release")
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("ORDER_NOT_FOUND"));
        api.get("/api/ops/jobs/" + unknown)
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("JOB_NOT_FOUND"));
        api.post("/api/ops/attempts/" + unknown + "/reconcile")
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("ATTEMPT_NOT_FOUND"));
        api.clearBedRequest(unknown.toString(), UUID.randomUUID().toString())
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("PRINTER_NOT_FOUND"));
        api.startAttemptRequest(api.releasedJob(), unknown.toString())
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("PRINTER_NOT_FOUND"));
    }

    @Test
    void malformedOperatorRequestsAreRejected() throws Exception {
        String jobId = api.releasedJob();
        api.post("/api/ops/jobs/" + jobId + "/attempts", "{}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("printerId"));

        String attemptId = api.startAttempt(jobId, printerId);
        api.post("/api/ops/printers/" + printerId + "/clear-bed", "{}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("attemptId"));
        api.post("/api/ops/attempts/" + attemptId + "/qc", """
                        {"result": "MAYBE"}
                        """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value("result"));
    }

    private static void expectConflict(ResultActions response, String code) throws Exception {
        response.andExpect(status().isConflict()).andExpect(jsonPath("$.code").value(code));
    }

    private String jobBody(String jobId) throws Exception {
        return api.get("/api/ops/jobs/" + jobId)
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    private Map<String, Object> printerRow() {
        return jdbc.sql("SELECT bed_state, current_attempt_id, bed_state_changed_at FROM printer WHERE id = ?::uuid")
                .param(printerId).query().singleRow();
    }
}
