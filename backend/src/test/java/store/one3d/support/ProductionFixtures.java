package store.one3d.support;

import org.springframework.jdbc.core.simple.JdbcClient;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Inserts production rows with plain SQL. Each test gets its own printer, so
 * tests never depend on the state of the seeded demo printer or of each other.
 */
public final class ProductionFixtures {

    /** Recipe revision 1 of the seeded demo configuration. */
    public static final UUID DEMO_RECIPE_REVISION_ID = UUID.fromString("0d3d0000-0000-4000-8000-000000000003");

    private final JdbcClient jdbc;

    public ProductionFixtures(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** A new simulated printer that is idle with a cleared bed. */
    public UUID clearPrinter() {
        UUID printerId = UUID.randomUUID();
        jdbc.sql("""
                        INSERT INTO printer (id, name, simulated, bed_state, bed_state_changed_at)
                        VALUES (?, ?, true, 'CLEAR', now())
                        """)
                .params(printerId, "TEST-" + printerId)
                .update();
        return printerId;
    }

    /** A bare order row (no line), enough for production foreign keys. */
    public UUID insertOrder(String status, String confirmationSource) {
        UUID orderId = UUID.randomUUID();
        OffsetDateTime confirmedAt = confirmationSource == null ? null : OffsetDateTime.now();
        jdbc.sql("""
                        INSERT INTO customer_order (id, status, created_at, confirmed_at, confirmation_source)
                        VALUES (?, ?, now(), ?, ?)
                        """)
                .params(orderId, status, confirmedAt, confirmationSource)
                .update();
        return orderId;
    }

    public UUID insertJob(UUID orderId) {
        UUID jobId = UUID.randomUUID();
        jdbc.sql("""
                        INSERT INTO production_job (id, order_id, recipe_revision_id, status, released_at)
                        VALUES (?, ?, ?, 'RELEASED', now())
                        """)
                .params(jobId, orderId, DEMO_RECIPE_REVISION_ID)
                .update();
        return jobId;
    }

    /** A start command for an attempt, as the backend records it before dispatch. */
    public UUID insertCommand(UUID attemptId, UUID printerId, UUID jobId) {
        UUID commandId = UUID.randomUUID();
        jdbc.sql("""
                        INSERT INTO printer_command (id, attempt_id, printer_id, job_id, payload_hash, status,
                                                     created_at, updated_at)
                        VALUES (?, ?, ?, ?, 'hash', 'PENDING', now(), now())
                        """)
                .params(commandId, attemptId, printerId, jobId)
                .update();
        return commandId;
    }

    public UUID insertAttempt(UUID jobId, int attemptNumber, UUID printerId, String status,
                              String failureReason, String qcResult) {
        UUID attemptId = UUID.randomUUID();
        boolean ended = !"RUNNING".equals(status);
        jdbc.sql("""
                        INSERT INTO print_attempt (id, job_id, attempt_number, printer_id, status, started_at,
                                                   ended_at, failure_reason, qc_result, qc_recorded_at)
                        VALUES (?, ?, ?, ?, ?, now(), ?, ?, ?, ?)
                        """)
                .params(attemptId, jobId, attemptNumber, printerId, status,
                        ended ? OffsetDateTime.now() : null, failureReason, qcResult,
                        qcResult == null ? null : OffsetDateTime.now())
                .update();
        return attemptId;
    }
}
