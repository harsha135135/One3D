package store.one3d.production;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import store.one3d.common.ConflictException;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/**
 * One execution of a production job on a printer. A retry is a new attempt;
 * an ended attempt keeps its outcome, reason and QC result. Change it only
 * through {@link ProductionJob}, which keeps the job and printer in step.
 */
@Entity
@Table(name = "print_attempt")
class PrintAttempt {

    // Matches print_attempt.failure_reason. A long external diagnostic must not
    // prevent saving the terminal outcome and leave the printer reserved forever.
    private static final int MAX_FAILURE_REASON = 500;

    // Assigned here rather than generated, because the printer records the
    // attempt id as its owner before the attempt row is inserted.
    @Id
    private UUID id;

    // A wrapper type: null marks a new attempt, so Spring Data persists it
    // instead of merging (the id is assigned in the constructor).
    @Version
    private Long version;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "job_id")
    private ProductionJob job;

    @Column(name = "attempt_number")
    private int attemptNumber;

    @Column(name = "printer_id")
    private UUID printerId;

    @Enumerated(EnumType.STRING)
    private AttemptStatus status;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "ended_at")
    private Instant endedAt;

    @Column(name = "failure_reason")
    private String failureReason;

    @Enumerated(EnumType.STRING)
    @Column(name = "qc_result")
    private QcResult qcResult;

    @Column(name = "qc_notes")
    private String qcNotes;

    @Column(name = "qc_recorded_at")
    private Instant qcRecordedAt;

    protected PrintAttempt() {
        // for JPA
    }

    /** A new attempt has been decided and recorded, but the printer has not confirmed it yet. */
    PrintAttempt(ProductionJob job, int attemptNumber, UUID printerId, Instant startedAt) {
        this.id = UUID.randomUUID();
        this.job = job;
        this.attemptNumber = attemptNumber;
        this.printerId = printerId;
        this.status = AttemptStatus.START_INTENT;
        this.startedAt = startedAt;
    }

    /** The simulator's journal confirms the start. */
    void markRunning() {
        require(Set.of(AttemptStatus.START_INTENT, AttemptStatus.RECONCILIATION_REQUIRED, AttemptStatus.RUNNING));
        status = AttemptStatus.RUNNING;
    }

    /** The start was sent but its outcome is unknown. Ownership is kept until reconciled. */
    void markReconciliationRequired() {
        require(Set.of(AttemptStatus.START_INTENT, AttemptStatus.RECONCILIATION_REQUIRED));
        status = AttemptStatus.RECONCILIATION_REQUIRED;
    }

    /** The simulator refused the start; nothing was printed. */
    void rejectBeforeStart(String reason, Instant now) {
        require(Set.of(AttemptStatus.START_INTENT, AttemptStatus.RECONCILIATION_REQUIRED));
        status = AttemptStatus.REJECTED_BEFORE_START;
        failureReason = reason;
        endedAt = now;
    }

    void complete(Instant now) {
        require(Set.of(AttemptStatus.RUNNING));
        status = AttemptStatus.COMPLETED;
        endedAt = now;
    }

    void fail(String reason, Instant now) {
        require(Set.of(AttemptStatus.RUNNING));
        status = AttemptStatus.FAILED;
        int characters = reason.codePointCount(0, reason.length());
        failureReason = characters > MAX_FAILURE_REASON
                ? reason.substring(0, reason.offsetByCodePoints(0, MAX_FAILURE_REASON)) : reason;
        endedAt = now;
    }

    void recordQc(QcResult result, String notes, Instant now) {
        if (status != AttemptStatus.COMPLETED) {
            throw new ConflictException("ATTEMPT_NOT_COMPLETED",
                    "Attempt " + attemptNumber + " is " + status + "; only a completed print can be inspected.");
        }
        if (qcResult != null) {
            throw new ConflictException("QC_ALREADY_RECORDED",
                    "Attempt " + attemptNumber + " already has QC result " + qcResult + ".");
        }
        qcResult = result;
        qcNotes = notes;
        qcRecordedAt = now;
    }

    /** Attempts that reached the printer count toward the limit; refused starts do not. */
    boolean countsTowardLimit() {
        return status != AttemptStatus.REJECTED_BEFORE_START;
    }

    private void require(Set<AttemptStatus> allowed) {
        if (!allowed.contains(status)) {
            throw new ConflictException(status == AttemptStatus.START_INTENT || status == AttemptStatus.RECONCILIATION_REQUIRED
                    ? "ATTEMPT_NOT_STARTED" : "ATTEMPT_NOT_RUNNING",
                    "Attempt " + attemptNumber + " is " + status + ".");
        }
    }

    UUID getId() {
        return id;
    }

    ProductionJob getJob() {
        return job;
    }

    int getAttemptNumber() {
        return attemptNumber;
    }

    UUID getPrinterId() {
        return printerId;
    }

    AttemptStatus getStatus() {
        return status;
    }

    Instant getStartedAt() {
        return startedAt;
    }

    Instant getEndedAt() {
        return endedAt;
    }

    String getFailureReason() {
        return failureReason;
    }

    QcResult getQcResult() {
        return qcResult;
    }

    String getQcNotes() {
        return qcNotes;
    }

    Instant getQcRecordedAt() {
        return qcRecordedAt;
    }
}
