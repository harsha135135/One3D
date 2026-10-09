package store.one3d.production;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.UUID;

/**
 * An operator's confirmation that the print of one attempt was removed from the
 * bed. The next start on the printer consumes it and passes it to the simulator.
 */
@Entity
@Table(name = "bed_clearance")
class BedClearance {

    @Id
    private UUID id;

    // Null marks a new row, so Spring Data persists it instead of merging (assigned id).
    @Version
    private Long version;

    @Column(name = "printer_id", updatable = false)
    private UUID printerId;

    @Column(name = "cleared_attempt_id", updatable = false)
    private UUID clearedAttemptId;

    @Column(name = "consumed_by_attempt_id")
    private UUID consumedByAttemptId;

    @Column(name = "cleared_at", updatable = false)
    private Instant clearedAt;

    protected BedClearance() {
        // for JPA
    }

    BedClearance(UUID printerId, UUID clearedAttemptId, Instant clearedAt) {
        this.id = UUID.randomUUID();
        this.printerId = printerId;
        this.clearedAttemptId = clearedAttemptId;
        this.clearedAt = clearedAt;
    }

    void consumeFor(UUID attemptId) {
        consumedByAttemptId = attemptId;
    }

    /** The start that used this clearance was refused, so the clearance is still valid. */
    void release() {
        consumedByAttemptId = null;
    }

    UUID getClearedAttemptId() {
        return clearedAttemptId;
    }

    UUID getConsumedByAttemptId() {
        return consumedByAttemptId;
    }
}
