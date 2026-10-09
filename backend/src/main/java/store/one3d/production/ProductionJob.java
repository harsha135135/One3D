package store.one3d.production;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import store.one3d.common.ConflictException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * The manufacturing work for one order: planned output, attempts and QC.
 * All job, attempt and printer state changes go through these methods so the
 * three stay consistent. See {@link JobStatus} and {@link AttemptStatus}.
 */
@Entity
@Table(name = "production_job")
class ProductionJob {

    /**
     * Attempts allowed per job in total, whether earlier ones failed to print or
     * failed QC. Starts the simulator refused (nothing printed) do not count.
     */
    static final int MAX_ATTEMPTS = 3;

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Version
    private long version;

    // Plain ids: the order and recipe belong to other modules.
    @Column(name = "order_id", updatable = false)
    private UUID orderId;

    @Column(name = "recipe_revision_id", updatable = false)
    private UUID recipeRevisionId;

    @Enumerated(EnumType.STRING)
    private JobStatus status;

    @Column(name = "released_at", updatable = false)
    private Instant releasedAt;

    @Column(name = "accepted_at")
    private Instant acceptedAt;

    @OneToMany(mappedBy = "job", cascade = CascadeType.PERSIST)
    @OrderBy("attemptNumber")
    private List<PrintAttempt> attempts = new ArrayList<>();

    protected ProductionJob() {
        // for JPA
    }

    /** An operator authorizes production of an order's accepted recipe revision. */
    static ProductionJob release(UUID orderId, UUID recipeRevisionId, Instant now) {
        ProductionJob job = new ProductionJob();
        job.orderId = orderId;
        job.recipeRevisionId = recipeRevisionId;
        job.status = JobStatus.RELEASED;
        job.releasedAt = now;
        return job;
    }

    /**
     * Records the intent to start the next attempt and reserves the printer for
     * it. The printer is only told afterwards, by the dispatch worker. The
     * operator's request is the authorization: nothing retries by itself.
     */
    PrintAttempt startAttempt(Printer printer, Instant now) {
        requireStatus(JobStatus.RELEASED, "JOB_NOT_RELEASED");
        // Checked before the printer is touched: a job at its limit leaves the
        // printer as it was, even when the printer could take the print.
        long counted = attempts.stream().filter(PrintAttempt::countsTowardLimit).count();
        if (counted >= MAX_ATTEMPTS) {
            throw new ConflictException("ATTEMPT_LIMIT_REACHED",
                    "Job " + id + " already has " + counted + " of " + MAX_ATTEMPTS + " attempts.");
        }
        PrintAttempt attempt = new PrintAttempt(this, attempts.size() + 1, printer.getId(), now);
        // Reserve before changing the job, so a busy printer or an occupied
        // bed leaves the job untouched.
        printer.reserveFor(attempt.getId(), now);
        attempts.add(attempt);
        status = JobStatus.PRINTING;
        return attempt;
    }

    /** The simulator's journal shows the attempt started. */
    void confirmStarted(PrintAttempt attempt) {
        requireOwnAttempt(attempt);
        attempt.markRunning();
    }

    /** The start was sent but its outcome is unknown; the attempt keeps the printer and the job. */
    void startOutcomeUnknown(PrintAttempt attempt) {
        requireOwnAttempt(attempt);
        attempt.markReconciliationRequired();
    }

    /** The simulator refused the start. Nothing printed, so the reservation is undone. */
    void rejectBeforeStart(PrintAttempt attempt, Printer printer, String reason, Instant now) {
        requireOwnAttempt(attempt);
        attempt.rejectBeforeStart(reason, now);
        printer.undoReservation(attempt.getId(), now);
        status = JobStatus.RELEASED;
    }

    /** The printer finished the attempt. The part now needs QC; the bed is not cleared. */
    void completeAttempt(PrintAttempt attempt, Printer printer, Instant now) {
        requireOwnAttempt(attempt);
        attempt.complete(now);
        printer.releaseFrom(attempt.getId());
        status = JobStatus.AWAITING_QC;
    }

    /** The attempt failed. It stays on record, and the job waits for another attempt. */
    void failAttempt(PrintAttempt attempt, Printer printer, String reason, Instant now) {
        requireOwnAttempt(attempt);
        attempt.fail(reason, now);
        printer.releaseFrom(attempt.getId());
        status = JobStatus.RELEASED;
    }

    /** Whole-part inspection. Only a pass accepts the job; a failure needs a new attempt. */
    void recordQc(PrintAttempt attempt, QcResult result, String notes, Instant now) {
        requireOwnAttempt(attempt);
        requireStatus(JobStatus.AWAITING_QC, "JOB_NOT_AWAITING_QC");
        attempt.recordQc(result, notes, now);
        if (result == QcResult.PASSED) {
            status = JobStatus.ACCEPTED;
            acceptedAt = now;
        } else {
            status = JobStatus.RELEASED;
        }
    }

    /** Readiness for handoff requires a part that passed QC. */
    void requireAccepted() {
        requireStatus(JobStatus.ACCEPTED, "JOB_NOT_ACCEPTED");
    }

    private void requireStatus(JobStatus expected, String code) {
        if (status != expected) {
            throw new ConflictException(code, "Job " + id + " is " + status + ", not " + expected + ".");
        }
    }

    private void requireOwnAttempt(PrintAttempt attempt) {
        // Compare ids, not references: attempt.getJob() may be a Hibernate proxy.
        if (!Objects.equals(id, attempt.getJob().getId())) {
            throw new IllegalArgumentException("Attempt " + attempt.getId() + " belongs to another job");
        }
    }

    UUID getId() {
        return id;
    }

    UUID getOrderId() {
        return orderId;
    }

    UUID getRecipeRevisionId() {
        return recipeRevisionId;
    }

    JobStatus getStatus() {
        return status;
    }

    Instant getReleasedAt() {
        return releasedAt;
    }

    Instant getAcceptedAt() {
        return acceptedAt;
    }

    List<PrintAttempt> getAttempts() {
        return List.copyOf(attempts);
    }
}
