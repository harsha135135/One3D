package store.one3d.production;

import com.jayway.jsonpath.JsonPath;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The production workflow end to end, with the printer simulator as a separate
 * process: an order goes through release, a dispatched print and QC to
 * readiness for handoff; failed attempts are kept; the bed is never cleared
 * automatically; a failed QC blocks handoff.
 */
@PostgresIntegrationTest
class ProductionWorkflowIT {

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
    private String printerId;

    @BeforeEach
    void setUp() {
        api = new ApiSteps(mvc, worker, observer, simulator);
        printerId = new ProductionFixtures(jdbc).clearPrinter().toString();
    }

    @Test
    void anOrderGoesFromReleaseThroughADispatchedPrintAndQcToReadyForHandoff() throws Exception {
        String orderId = api.placeOrder();

        api.pay(orderId);
        api.get("/api/orders/" + orderId)
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.confirmationSource").value("RAZORPAY_TEST"))
                .andExpect(jsonPath("$.confirmedAt").value(notNullValue()));
        // Payment alone starts nothing: the order waits for an operator to release it.
        api.get("/api/ops/orders/" + orderId).andExpect(jsonPath("$.jobId").value(nullValue()));

        String released = api.post("/api/ops/orders/" + orderId + "/release")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("RELEASED"))
                .andExpect(jsonPath("$.orderId").value(orderId))
                .andExpect(jsonPath("$.recipeRevisionId").value(ProductionFixtures.DEMO_RECIPE_REVISION_ID.toString()))
                .andExpect(jsonPath("$.attempts", hasSize(0)))
                .andReturn().getResponse().getContentAsString();
        String jobId = JsonPath.read(released, "$.id");

        // The operator's request records the intent; the simulator is told afterwards.
        api.startAttemptRequest(jobId, printerId)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PRINTING"))
                .andExpect(jsonPath("$.attempts[0].attemptNumber").value(1))
                .andExpect(jsonPath("$.attempts[0].status").value("START_INTENT"))
                .andExpect(jsonPath("$.attempts[0].printerId").value(printerId));
        String attemptId = JsonPath.read(api.jobJson(jobId), "$.attempts[0].id");
        expectPrinter("OCCUPIED", attemptId);

        api.dispatch();
        api.get("/api/ops/jobs/" + jobId).andExpect(jsonPath("$.attempts[0].status").value("RUNNING"));
        assertThat(simulator.printer(printerId)).contains("\"runningAttemptId\":\"" + attemptId + "\"");

        simulator.complete(printerId);
        api.observe();
        api.get("/api/ops/jobs/" + jobId)
                .andExpect(jsonPath("$.status").value("AWAITING_QC"))
                .andExpect(jsonPath("$.attempts[0].status").value("COMPLETED"))
                .andExpect(jsonPath("$.attempts[0].endedAt").value(notNullValue()));
        expectPrinter("OCCUPIED", null); // free, but the part is still on the bed

        api.qc(attemptId, "PASSED")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACCEPTED"))
                .andExpect(jsonPath("$.acceptedAt").value(notNullValue()))
                .andExpect(jsonPath("$.attempts[0].qcResult").value("PASSED"));

        api.post("/api/ops/orders/" + orderId + "/ready-for-handoff")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("READY_FOR_HANDOFF"))
                .andExpect(jsonPath("$.readyForHandoffAt").value(notNullValue()));
        api.get("/api/orders/" + orderId)
                .andExpect(jsonPath("$.status").value("READY_FOR_HANDOFF"));
        api.post("/api/ops/orders/" + orderId + "/handed-over")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("HANDED_OVER"))
                .andExpect(jsonPath("$.handedOverAt").value(notNullValue()));
        // The customer's timeline, built from facts each step recorded.
        api.get("/api/orders/" + orderId + "/timeline")
                .andExpect(jsonPath("$[*].kind").value(contains("PLACED", "CONFIRMED", "RELEASED_TO_PRODUCTION",
                        "QC_PASSED", "READY_FOR_HANDOFF", "HANDED_OVER")));

        api.clearBedRequest(printerId, attemptId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bedState").value("CLEAR"))
                .andExpect(jsonPath("$.bedCycleAttemptId").value(nullValue()));
    }

    @Test
    void aFailedAttemptIsKeptAndARetryCreatesAttemptTwo() throws Exception {
        String jobId = api.releasedJob();
        String firstAttempt = api.startAttempt(jobId, printerId);

        api.fail(firstAttempt, "First layer detached");
        api.get("/api/ops/jobs/" + jobId)
                .andExpect(jsonPath("$.status").value("RELEASED"))
                .andExpect(jsonPath("$.attempts[0].status").value("FAILED"))
                .andExpect(jsonPath("$.attempts[0].failureReason").value("First layer detached"));
        expectPrinter("OCCUPIED", null);

        // No retry happens by itself, and the failed print still blocks the bed.
        api.startAttemptRequest(jobId, printerId)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("BED_NOT_CLEAR"));
        api.get("/api/ops/jobs/" + jobId)
                .andExpect(jsonPath("$.status").value("RELEASED"))
                .andExpect(jsonPath("$.attempts", hasSize(1)));

        api.clearBed(printerId);
        api.startAttempt(jobId, printerId);
        api.get("/api/ops/jobs/" + jobId)
                .andExpect(jsonPath("$.attempts", hasSize(2)))
                .andExpect(jsonPath("$.attempts[0].attemptNumber").value(1))
                .andExpect(jsonPath("$.attempts[0].status").value("FAILED"))
                .andExpect(jsonPath("$.attempts[0].failureReason").value("First layer detached"))
                .andExpect(jsonPath("$.attempts[1].attemptNumber").value(2))
                .andExpect(jsonPath("$.attempts[1].status").value("RUNNING"));
    }

    @Test
    void aCompletedPrintKeepsTheBedOccupiedUntilAnOperatorClearsIt() throws Exception {
        String firstJob = api.releasedJob();
        String secondJob = api.releasedJob();
        api.complete(api.startAttempt(firstJob, printerId));

        api.startAttemptRequest(secondJob, printerId)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("BED_NOT_CLEAR"));

        api.clearBed(printerId);
        api.startAttempt(secondJob, printerId);
        api.get("/api/ops/jobs/" + secondJob)
                .andExpect(jsonPath("$.status").value("PRINTING"))
                .andExpect(jsonPath("$.attempts[0].status").value("RUNNING"));
    }

    @Test
    void aFailedQcBlocksHandoffUntilAReplacementPasses() throws Exception {
        String orderId = api.placeConfirmedOrder();
        String jobId = api.release(orderId);
        String firstAttempt = api.startAttempt(jobId, printerId);
        api.complete(firstAttempt);

        api.qc(firstAttempt, "FAILED")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RELEASED"))
                .andExpect(jsonPath("$.attempts[0].status").value("COMPLETED"))
                .andExpect(jsonPath("$.attempts[0].qcResult").value("FAILED"));

        api.post("/api/ops/orders/" + orderId + "/ready-for-handoff")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("JOB_NOT_ACCEPTED"));
        api.get("/api/orders/" + orderId)
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.readyForHandoffAt").value(nullValue()));

        // An operator explicitly starts a replacement attempt, which passes.
        api.clearBed(printerId);
        String secondAttempt = api.startAttempt(jobId, printerId);
        api.complete(secondAttempt);
        api.qc(secondAttempt, "PASSED").andExpect(jsonPath("$.status").value("ACCEPTED"));

        api.post("/api/ops/orders/" + orderId + "/ready-for-handoff")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("READY_FOR_HANDOFF"));
    }

    @Test
    void confirmingAnOrderCreatesNoJobAndSendsNothingToAPrinter() throws Exception {
        String orderId = api.placeConfirmedOrder();
        api.dispatch();

        long jobs = jdbc.sql("SELECT count(*) FROM production_job WHERE order_id = ?::uuid")
                .param(orderId).query(Long.class).single();
        long attemptsOnPrinter = jdbc.sql("SELECT count(*) FROM print_attempt WHERE printer_id = ?::uuid")
                .param(printerId).query(Long.class).single();
        long commandsForPrinter = jdbc.sql("SELECT count(*) FROM printer_command WHERE printer_id = ?::uuid")
                .param(printerId).query(Long.class).single();

        assertThat(jobs).isZero();
        assertThat(attemptsOnPrinter).isZero();
        assertThat(commandsForPrinter).isZero();
        assertThat(simulator.printer(printerId)).startsWith("404");
        expectPrinter("CLEAR", null);
    }

    private void expectPrinter(String bedState, String currentAttemptId) throws Exception {
        String printer = "$[?(@.id == '" + printerId + "')]";
        ResultActions printers = api.get("/api/ops/printers").andExpect(status().isOk());
        printers.andExpect(jsonPath(printer + ".bedState", contains(bedState)));
        printers.andExpect(jsonPath(printer + ".currentAttemptId", contains(currentAttemptId)));
    }
}
