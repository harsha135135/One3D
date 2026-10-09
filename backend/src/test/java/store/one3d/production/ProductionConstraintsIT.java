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

    private static void assertViolates(String constraint, Runnable statement) {
        assertThatThrownBy(statement::run)
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining(constraint);
    }
}
