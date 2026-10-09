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

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The Checkpoint 2 demonstrations: an order goes through release, printing and
 * QC to readiness for handoff; failed attempts are kept; the bed is never
 * cleared automatically; a failed QC blocks handoff.
 */
@PostgresIntegrationTest
class ProductionWorkflowIT {

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcClient jdbc;

    private ApiSteps api;
    private String printerId;

    @BeforeEach
    void setUp() {
        api = new ApiSteps(mvc);
        printerId = new ProductionFixtures(jdbc).clearPrinter().toString();
    }

    @Test
    void anOrderGoesFromReleaseThroughPrintAndQcToReadyForHandoff() throws Exception {
        String orderId = api.placeOrder();

        api.post("/api/demo/orders/" + orderId + "/simulate-payment")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.confirmationSource").value("DEMO_SIMULATED"))
                .andExpect(jsonPath("$.confirmedAt").value(notNullValue()));

        String released = api.post("/api/ops/orders/" + orderId + "/release")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("RELEASED"))
                .andExpect(jsonPath("$.orderId").value(orderId))
                .andExpect(jsonPath("$.recipeRevisionId").value(ProductionFixtures.DEMO_RECIPE_REVISION_ID.toString()))
                .andExpect(jsonPath("$.attempts", hasSize(0)))
                .andReturn().getResponse().getContentAsString();
        String jobId = JsonPath.read(released, "$.id");
        api.get("/api/ops/jobs/" + jobId).andExpect(status().isOk());

        api.startAttemptRequest(jobId, printerId)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PRINTING"))
                .andExpect(jsonPath("$.attempts[0].attemptNumber").value(1))
                .andExpect(jsonPath("$.attempts[0].status").value("RUNNING"))
                .andExpect(jsonPath("$.attempts[0].printerId").value(printerId));
        String attemptId = attemptId(jobId, 0);
        expectPrinter("OCCUPIED", attemptId);

        api.post("/api/ops/attempts/" + attemptId + "/simulate-completion")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("AWAITING_QC"))
                .andExpect(jsonPath("$.attempts[0].status").value("COMPLETED"))
                .andExpect(jsonPath("$.attempts[0].endedAt").value(notNullValue()));
        expectPrinter("OCCUPIED", null); // idle, but the part is still on the bed

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

        api.post("/api/ops/printers/" + printerId + "/clear-bed")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bedState").value("CLEAR"));
    }

    @Test
    void aFailedAttemptIsKeptAndARetryCreatesAttemptTwo() throws Exception {
        String jobId = api.releasedJob();
        String firstAttempt = api.startAttempt(jobId, printerId);

        api.post("/api/ops/attempts/" + firstAttempt + "/simulate-failure", """
                        {"reason": "First layer detached"}
                        """)
                .andExpect(status().isOk())
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
        api.startAttemptRequest(jobId, printerId)
                .andExpect(status().isCreated())
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
        api.startAttemptRequest(secondJob, printerId)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PRINTING"));
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
    void confirmingAnOrderCreatesNoJobAndLeavesPrintersUntouched() throws Exception {
        String orderId = api.placeConfirmedOrder();

        long jobs = jdbc.sql("SELECT count(*) FROM production_job WHERE order_id = ?::uuid")
                .param(orderId).query(Long.class).single();
        long attemptsOnPrinter = jdbc.sql("SELECT count(*) FROM print_attempt WHERE printer_id = ?::uuid")
                .param(printerId).query(Long.class).single();

        assertThat(jobs).isZero();
        assertThat(attemptsOnPrinter).isZero();
        expectPrinter("CLEAR", null);
    }

    private String attemptId(String jobId, int index) throws Exception {
        String body = api.get("/api/ops/jobs/" + jobId).andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.attempts[" + index + "].id");
    }

    private void expectPrinter(String bedState, String currentAttemptId) throws Exception {
        String printer = "$[?(@.id == '" + printerId + "')]";
        ResultActions printers = api.get("/api/ops/printers").andExpect(status().isOk());
        printers.andExpect(jsonPath(printer + ".bedState", contains(bedState)));
        printers.andExpect(jsonPath(printer + ".currentAttemptId", contains(currentAttemptId)));
    }
}
