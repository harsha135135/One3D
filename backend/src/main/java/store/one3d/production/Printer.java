package store.one3d.production;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import store.one3d.common.ConflictException;

import java.time.Instant;
import java.util.UUID;

/**
 * The simulated printer as the backend records it. Two facts are kept apart:
 * <ul>
 *   <li>{@code currentAttemptId}: the active attempt that owns the printer (null = free);</li>
 *   <li>{@code bedState} and {@code bedCycleAttemptId}: whether the bed is free, and
 *       if not, which attempt's print is on it.</li>
 * </ul>
 * A finished or failed print frees the printer but leaves its print on the bed.
 * Only an operator clearance that names that print makes the bed CLEAR again,
 * and the next start uses up that clearance.
 */
@Entity
@Table(name = "printer")
class Printer {

    @Id
    private UUID id;

    @Version
    private long version;

    @Column(updatable = false)
    private String name;

    // Always true in V1 (database constraint printer_simulated_only).
    @Column(updatable = false)
    private boolean simulated;

    @Enumerated(EnumType.STRING)
    @Column(name = "bed_state")
    private BedState bedState;

    @Column(name = "bed_cycle_attempt_id")
    private UUID bedCycleAttemptId;

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

    /** Takes a free printer with a cleared bed for one attempt; its print will occupy the bed. */
    void reserveFor(UUID attemptId, Instant now) {
        if (currentAttemptId != null) {
            throw new ConflictException("PRINTER_BUSY",
                    "Printer " + name + " is in use by attempt " + currentAttemptId + ".");
        }
        if (bedState != BedState.CLEAR) {
            throw new ConflictException("BED_NOT_CLEAR",
                    "Printer " + name + " has not been cleared since its last print.");
        }
        currentAttemptId = attemptId;
        bedState = BedState.OCCUPIED;
        bedCycleAttemptId = attemptId;
        bedStateChangedAt = now;
    }

    /** The attempt ended. The printer is free; its print stays on the bed. */
    void releaseFrom(UUID attemptId) {
        requireOwnedBy(attemptId);
        currentAttemptId = null;
    }

    /**
     * The simulator refused the start, so nothing was printed: return to the
     * state before the reservation, which always had a cleared bed.
     */
    void undoReservation(UUID attemptId, Instant now) {
        requireOwnedBy(attemptId);
        currentAttemptId = null;
        bedState = BedState.CLEAR;
        bedCycleAttemptId = null;
        bedStateChangedAt = now;
    }

    /**
     * An operator confirms that the print of {@code clearedAttemptId} was removed.
     * It must be the print on the bed now: a delayed request for an earlier print
     * is refused instead of clearing a later one.
     */
    void clearBed(UUID clearedAttemptId, Instant now) {
        if (currentAttemptId != null) {
            throw new ConflictException("PRINTER_BUSY",
                    "Printer " + name + " is still in use by attempt " + currentAttemptId + ".");
        }
        if (bedState == BedState.CLEAR) {
            throw new ConflictException("BED_ALREADY_CLEAR", "Printer " + name + " is already clear.");
        }
        if (!bedCycleAttemptId.equals(clearedAttemptId)) {
            throw new ConflictException("STALE_CLEARANCE",
                    "The bed holds the print of attempt " + bedCycleAttemptId + ", not of " + clearedAttemptId + ".");
        }
        bedState = BedState.CLEAR;
        bedCycleAttemptId = null;
        bedStateChangedAt = now;
    }

    private void requireOwnedBy(UUID attemptId) {
        if (!attemptId.equals(currentAttemptId)) {
            throw new ConflictException("PRINTER_NOT_RUNNING_ATTEMPT",
                    "Printer " + name + " is not owned by attempt " + attemptId + ".");
        }
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

    UUID getBedCycleAttemptId() {
        return bedCycleAttemptId;
    }

    UUID getCurrentAttemptId() {
        return currentAttemptId;
    }

    Instant getBedStateChangedAt() {
        return bedStateChangedAt;
    }
}
