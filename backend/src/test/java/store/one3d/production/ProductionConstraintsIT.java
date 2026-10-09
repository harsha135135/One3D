package store.one3d.production;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;
import store.one3d.support.PostgresIntegrationTest;
import store.one3d.support.ProductionFixtures;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Writes production rows with plain SQL, bypassing the Java rules, to show
 * which rules PostgreSQL enforces on its own. Each test is rolled back.
 */
@PostgresIntegrationTest
@Transactional
class ProductionConstraintsIT {

    @Autowired
    JdbcClient jdbc;

    private ProductionFixtures fixtures;
    private UUID printerId;
    private UUID jobId;

    @BeforeEach
    void setUp() {
        fixtures = new ProductionFixtures(jdbc);
        printerId = fixtures.clearPrinter();
        jobId = fixtures.insertJob(fixtures.insertOrder("CONFIRMED", "DEMO_SIMULATED"));
    }

    @Test
    void aPrinterMustBeSimulated() {
        assertViolates("printer_simulated_only", () -> jdbc.sql("""
                INSERT INTO printer (id, name, simulated, bed_state, bed_state_changed_at)
                VALUES (gen_random_uuid(), 'REAL-PRINTER', false, 'CLEAR', now())
                """).update());
    }

    @Test
    void aRunningAttemptMustOccupyTheBed() {
        UUID attemptId = fixtures.insertAttempt(jobId, 1, printerId, "RUNNING", null, null);

        assertViolates("printer_running_attempt_occupies_bed", () -> jdbc.sql("""
                        UPDATE printer SET current_attempt_id = ?, bed_state = 'CLEAR' WHERE id = ?
                        """)
                .params(attemptId, printerId)
                .update());
    }

    @Test
    void anOrderHasAtMostOneJob() {
        UUID orderId = fixtures.insertOrder("CONFIRMED", "DEMO_SIMULATED");
        fixtures.insertJob(orderId);

        assertViolates("production_job_order_unique", () -> fixtures.insertJob(orderId));
    }

    @Test
    void attemptNumbersAreUniqueWithinAJob() {
        fixtures.insertAttempt(jobId, 1, printerId, "FAILED", "Clog", null);

        assertViolates("print_attempt_number_unique",
                () -> fixtures.insertAttempt(jobId, 1, printerId, "RUNNING", null, null));
    }

    @Test
    void aFailedAttemptKeepsItsReason() {
        assertViolates("print_attempt_failure_has_reason",
                () -> fixtures.insertAttempt(jobId, 1, printerId, "FAILED", null, null));
    }

    @Test
    void onlyACompletedAttemptCanHaveAQcResult() {
        assertViolates("print_attempt_qc_only_when_completed",
                () -> fixtures.insertAttempt(jobId, 1, printerId, "FAILED", "Warping", "PASSED"));
    }

    // One violation per test: after an error PostgreSQL rejects further
    // statements in the same (rolled-back) transaction.

    @Test
    void aConfirmedOrderRecordsWhenAndHowItWasConfirmed() {
        assertViolates("customer_order_confirmation_matches_status",
                () -> fixtures.insertOrder("CONFIRMED", null));
    }

    @Test
    void confirmationSourceIsOnlyTheDemoSimulationForNow() {
        assertViolates("customer_order_confirmation_source_known",
                () -> fixtures.insertOrder("CONFIRMED", "BANK_TRANSFER"));
    }

    @Test
    void aPrinterHasAtMostOneActiveAttempt() {
        UUID otherJob = fixtures.insertJob(fixtures.insertOrder("CONFIRMED", "DEMO_SIMULATED"));
        fixtures.insertAttempt(jobId, 1, printerId, "RUNNING", null, null);

        assertViolates("print_attempt_one_active_per_printer",
                () -> fixtures.insertAttempt(otherJob, 1, printerId, "RECONCILIATION_REQUIRED", null, null));
    }

    @Test
    void aJobHasAtMostOneActiveAttempt() {
        UUID otherPrinter = fixtures.clearPrinter();
        fixtures.insertAttempt(jobId, 1, printerId, "START_INTENT", null, null);

        assertViolates("print_attempt_one_active_per_job",
                () -> fixtures.insertAttempt(jobId, 2, otherPrinter, "RUNNING", null, null));
    }

    @Test
    void anOccupiedBedNamesThePrintOnIt() {
        assertViolates("printer_bed_cycle_matches_state", () -> jdbc.sql(
                        "UPDATE printer SET bed_state = 'OCCUPIED', bed_cycle_attempt_id = NULL WHERE id = ?")
                .params(printerId)
                .update());
    }

    @Test
    void aPrinterHasAtMostOneUnusedClearance() {
        UUID first = fixtures.insertAttempt(jobId, 1, printerId, "FAILED", "Warping", null);
        UUID second = fixtures.insertAttempt(jobId, 2, printerId, "FAILED", "Warping", null);
        insertClearance(first);

        assertViolates("bed_clearance_one_open_per_printer", () -> insertClearance(second));
    }

    @Test
    void aClaimedWorkRowCarriesItsClaimToken() {
        UUID attemptId = fixtures.insertAttempt(jobId, 1, printerId, "START_INTENT", null, null);
        UUID commandId = fixtures.insertCommand(attemptId, printerId, jobId);

        assertViolates("pending_work_claim_matches_status", () -> jdbc.sql("""
                        INSERT INTO pending_work (id, kind, command_id, status, attempts, max_attempts,
                                                  available_at, created_at, updated_at)
                        VALUES (gen_random_uuid(), 'DISPATCH_START', ?, 'CLAIMED', 1, 5, now(), now(), now())
                        """)
                .params(commandId)
                .update());
    }

    private void insertClearance(UUID clearedAttemptId) {
        jdbc.sql("""
                        INSERT INTO bed_clearance (id, printer_id, cleared_attempt_id, cleared_at)
                        VALUES (gen_random_uuid(), ?, ?, now())
                        """)
                .params(printerId, clearedAttemptId)
                .update();
    }

    private static void assertViolates(String constraint, Runnable statement) {
        assertThatThrownBy(statement::run)
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining(constraint);
    }
}
