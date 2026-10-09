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
import store.one3d.common.ConflictException;

import java.time.Instant;
import java.util.UUID;

/**
 * One execution of a production job on a printer. A retry is a new attempt;
 * an ended attempt keeps its outcome, failure reason and QC result.
 * Change it only through {@link ProductionJob}, which keeps the job in step.
 */
@Entity
@Table(name = "print_attempt")
class PrintAttempt {

    // Assigned here rather than generated, because the printer records the
    // attempt id as its owner before the attempt row is inserted.
    @Id
    private UUID id;

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

    PrintAttempt(ProductionJob job, int attemptNumber, UUID printerId, Instant startedAt) {
        this.id = UUID.randomUUID();
        this.job = job;
        this.attemptNumber = attemptNumber;
        this.printerId = printerId;
        this.status = AttemptStatus.RUNNING;
        this.startedAt = startedAt;
    }

    void complete(Instant now) {
        requireRunning();
        status = AttemptStatus.COMPLETED;
        endedAt = now;
    }

    void fail(String reason, Instant now) {
        requireRunning();
        status = AttemptStatus.FAILED;
        failureReason = reason;
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

    private void requireRunning() {
        if (status != AttemptStatus.RUNNING) {
            throw new ConflictException("ATTEMPT_NOT_RUNNING",
                    "Attempt " + attemptNumber + " is " + status + ", not RUNNING.");
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
