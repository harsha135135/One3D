package store.one3d.production;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import store.one3d.common.ConflictException;

import java.time.Instant;
import java.util.UUID;

/**
 * The simulated printer. Two facts are kept apart:
 * <ul>
 *   <li>{@code currentAttemptId}: which attempt owns the printer right now (null = idle);</li>
 *   <li>{@code bedState}: whether the bed is free for the next print.</li>
 * </ul>
 * Finishing or failing a print makes the printer idle but leaves the bed
 * OCCUPIED. Only an operator clearing the bed makes it CLEAR again, and the
 * next start uses up that clearance.
 */
@Entity
@Table(name = "printer")
class Printer {

    @Id
    private UUID id;

    @Column(updatable = false)
    private String name;

    // Always true in V1 (database constraint printer_simulated_only).
    @Column(updatable = false)
    private boolean simulated;

    @Enumerated(EnumType.STRING)
    @Column(name = "bed_state")
    private BedState bedState;

    // A plain id rather than an entity reference keeps the printer and attempt
    // mappings independent; the foreign key is in the database.
    @Column(name = "current_attempt_id")
    private UUID currentAttemptId;

    @Column(name = "bed_state_changed_at")
    private Instant bedStateChangedAt;

    protected Printer() {
        // for JPA
    }

    /** An idle simulated printer with a cleared bed. V1 seeds its printer by migration; tests use this. */
    static Printer simulated(String name, Instant now) {
        Printer printer = new Printer();
        printer.id = UUID.randomUUID();
        printer.name = name;
        printer.simulated = true;
        printer.bedState = BedState.CLEAR;
        printer.bedStateChangedAt = now;
        return printer;
    }

    /** Takes ownership of an idle printer with a cleared bed for one attempt. */
    void reserveFor(UUID attemptId, Instant now) {
        if (currentAttemptId != null) {
            throw new ConflictException("PRINTER_BUSY",
                    "Printer " + name + " is running attempt " + currentAttemptId + ".");
        }
        if (bedState != BedState.CLEAR) {
            throw new ConflictException("BED_NOT_CLEAR",
                    "Printer " + name + " has not been cleared since its last print.");
        }
        currentAttemptId = attemptId;
        bedState = BedState.OCCUPIED;
        bedStateChangedAt = now;
    }

    /** The attempt has ended. The printer becomes idle; the bed stays occupied. */
    void releaseFrom(UUID attemptId) {
        if (!attemptId.equals(currentAttemptId)) {
            throw new ConflictException("PRINTER_NOT_RUNNING_ATTEMPT",
                    "Printer " + name + " is not running attempt " + attemptId + ".");
        }
        currentAttemptId = null;
    }

    /** An operator confirms the bed is empty and ready for the next print. */
    void clearBed(Instant now) {
        if (currentAttemptId != null) {
            throw new ConflictException("PRINTER_BUSY",
                    "Printer " + name + " is still running attempt " + currentAttemptId + ".");
        }
        if (bedState == BedState.CLEAR) {
            throw new ConflictException("BED_ALREADY_CLEAR", "Printer " + name + " is already clear.");
        }
        bedState = BedState.CLEAR;
        bedStateChangedAt = now;
    }

    UUID getId() {
        return id;
    }

    String getName() {
        return name;
    }

    boolean isSimulated() {
        return simulated;
    }

    BedState getBedState() {
        return bedState;
    }

    UUID getCurrentAttemptId() {
        return currentAttemptId;
    }

    Instant getBedStateChangedAt() {
        return bedStateChangedAt;
    }
}
