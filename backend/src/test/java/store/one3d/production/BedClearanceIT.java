package store.one3d.production;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import store.one3d.support.ApiSteps;
import store.one3d.support.PostgresIntegrationTest;
import store.one3d.support.ProductionFixtures;
import store.one3d.support.SimulatorRig;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * A bed clearance names the print it clears. A delayed or repeated clearance
 * can never clear a later print, in the backend or in the simulator.
 */
@PostgresIntegrationTest
class BedClearanceIT {

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
    void aDelayedClearanceForAnEarlierPrintLeavesTheLaterPrintOnTheBed() throws Exception {
        String jobId = api.releasedJob();
        String first = api.startAttempt(jobId, printerId);
        api.fail(first, "Warping");
        api.clearBed(printerId);                       // clearance for print 1
        String second = api.startAttempt(jobId, printerId);
        api.complete(second);                          // print 2 is now on the bed

        api.clearBedRequest(printerId, first)          // the clearance for print 1 arrives again
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STALE_CLEARANCE"));

        assertThat(api.bedCycleAttemptId(printerId)).isEqualTo(second);
        api.startAttemptRequest(api.releasedJob(), printerId)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("BED_NOT_CLEAR"));
    }

    @Test
    void aRepeatedClearanceIsRefusedAndRecordedOnce() throws Exception {
        String attemptId = api.startAttempt(api.releasedJob(), printerId);
        api.complete(attemptId);

        api.clearBedRequest(printerId, attemptId).andExpect(status().isOk());
        api.clearBedRequest(printerId, attemptId)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("BED_ALREADY_CLEAR"));

        long clearances = jdbc.sql("SELECT count(*) FROM bed_clearance WHERE cleared_attempt_id = ?::uuid")
                .param(attemptId).query(Long.class).single();
        assertThat(clearances).isEqualTo(1);
    }

    @Test
    void theSimulatorAlsoRefusesAStartCarryingAClearanceForAnEarlierPrint() throws Exception {
        String jobId = api.releasedJob();
        String first = api.startAttempt(jobId, printerId);
        api.fail(first, "Warping");
        api.clearBed(printerId);
        String second = api.startAttempt(jobId, printerId); // used the clearance of print 1
        api.complete(second);

        String reply = simulator.sendStart(UUID.randomUUID().toString(), printerId, "late-" + UUID.randomUUID(), first);

        assertThat(reply).contains("\"outcome\":\"REJECTED_BED_NOT_CLEAR\"");
        assertThat(simulator.printer(printerId)).contains("\"bedCycleAttemptId\":\"" + second + "\"");
    }
}
