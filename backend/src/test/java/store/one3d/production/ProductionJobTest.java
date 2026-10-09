package store.one3d.production;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import store.one3d.common.ConflictException;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The job, attempt and printer rules, without Spring or a database. */
class ProductionJobTest {

    private static final Instant NOW = Instant.parse("2026-10-09T08:00:00Z");

    private ProductionJob job;
    private Printer printer;

    @BeforeEach
    void setUp() {
        job = ProductionJob.release(UUID.randomUUID(), UUID.randomUUID(), NOW);
        printer = Printer.simulated("SIM-TEST", NOW);
    }

    @Test
    void releaseCreatesAJobWithoutAttempts() {
        assertThat(job.getStatus()).isEqualTo(JobStatus.RELEASED);
        assertThat(job.getAttempts()).isEmpty();
    }

    @Test
    void startingAnAttemptReservesThePrinterAndOccupiesTheBed() {
        PrintAttempt attempt = job.startAttempt(printer, NOW);

        assertThat(job.getStatus()).isEqualTo(JobStatus.PRINTING);
        assertThat(attempt.getAttemptNumber()).isEqualTo(1);
        assertThat(attempt.getStatus()).isEqualTo(AttemptStatus.RUNNING);
        assertThat(printer.getCurrentAttemptId()).isEqualTo(attempt.getId());
        assertThat(printer.getBedState()).isEqualTo(BedState.OCCUPIED);
    }

    @Test
    void completionNeedsQcAndLeavesTheBedOccupied() {
        PrintAttempt attempt = job.startAttempt(printer, NOW);

        job.completeAttempt(attempt, printer, NOW);

        assertThat(job.getStatus()).isEqualTo(JobStatus.AWAITING_QC);
        assertThat(attempt.getStatus()).isEqualTo(AttemptStatus.COMPLETED);
        assertThat(printer.getCurrentAttemptId()).isNull();
        assertThat(printer.getBedState()).isEqualTo(BedState.OCCUPIED);
    }

    @Test
    void failureKeepsTheAttemptAndARetryIsAttemptTwo() {
        PrintAttempt first = job.startAttempt(printer, NOW);
        job.failAttempt(first, printer, "Nozzle clog", NOW);

        assertThat(job.getStatus()).isEqualTo(JobStatus.RELEASED);
        assertThat(first.getStatus()).isEqualTo(AttemptStatus.FAILED);
        assertThat(first.getFailureReason()).isEqualTo("Nozzle clog");
        assertThat(printer.getBedState()).isEqualTo(BedState.OCCUPIED);

        printer.clearBed(NOW);
        PrintAttempt second = job.startAttempt(printer, NOW);

        assertThat(second.getAttemptNumber()).isEqualTo(2);
        assertThat(job.getAttempts()).extracting(PrintAttempt::getStatus)
                .containsExactly(AttemptStatus.FAILED, AttemptStatus.RUNNING);
    }

    @Test
    void anOccupiedBedBlocksTheNextStartAndLeavesTheJobUnchanged() {
        PrintAttempt first = job.startAttempt(printer, NOW);
        job.failAttempt(first, printer, "Warping", NOW);

        assertThatThrownBy(() -> job.startAttempt(printer, NOW))
                .isInstanceOf(ConflictException.class)
                .extracting("code").isEqualTo("BED_NOT_CLEAR");
        assertThat(job.getStatus()).isEqualTo(JobStatus.RELEASED);
        assertThat(job.getAttempts()).hasSize(1);
    }

    @Test
    void aFourthAttemptIsRejectedAndChangesNothing() {
        for (int i = 0; i < ProductionJob.MAX_ATTEMPTS; i++) {
            job.failAttempt(job.startAttempt(printer, NOW), printer, "Warping", NOW);
            printer.clearBed(NOW);
        }

        // A later instant, so a bed change would show in bedStateChangedAt.
        assertThatThrownBy(() -> job.startAttempt(printer, NOW.plusSeconds(60)))
                .isInstanceOf(ConflictException.class)
                .extracting("code").isEqualTo("ATTEMPT_LIMIT_REACHED");
        assertThat(job.getStatus()).isEqualTo(JobStatus.RELEASED);
        assertThat(job.getAttempts()).extracting(PrintAttempt::getAttemptNumber).containsExactly(1, 2, 3);
        assertThat(printer.getCurrentAttemptId()).isNull();
        assertThat(printer.getBedState()).isEqualTo(BedState.CLEAR);
        assertThat(printer.getBedStateChangedAt()).isEqualTo(NOW);
    }

    @Test
    void passingQcAcceptsTheJob() {
        PrintAttempt attempt = job.startAttempt(printer, NOW);
        job.completeAttempt(attempt, printer, NOW);

        job.recordQc(attempt, QcResult.PASSED, null, NOW);

        assertThat(job.getStatus()).isEqualTo(JobStatus.ACCEPTED);
        assertThat(job.getAcceptedAt()).isEqualTo(NOW);
        job.requireAccepted();
    }

    @Test
    void failingQcRequiresAnotherAttemptAndBlocksHandoff() {
        PrintAttempt attempt = job.startAttempt(printer, NOW);
        job.completeAttempt(attempt, printer, NOW);

        job.recordQc(attempt, QcResult.FAILED, "Layer shift", NOW);

        assertThat(job.getStatus()).isEqualTo(JobStatus.RELEASED);
        assertThatThrownBy(job::requireAccepted)
                .isInstanceOf(ConflictException.class)
                .extracting("code").isEqualTo("JOB_NOT_ACCEPTED");
    }

    @Test
    void aRunningAttemptCannotBeInspected() {
        PrintAttempt attempt = job.startAttempt(printer, NOW);

        assertThatThrownBy(() -> job.recordQc(attempt, QcResult.PASSED, null, NOW))
                .isInstanceOf(ConflictException.class)
                .extracting("code").isEqualTo("JOB_NOT_AWAITING_QC");
    }

    @Test
    void anEndedAttemptCannotEndAgain() {
        PrintAttempt attempt = job.startAttempt(printer, NOW);
        job.completeAttempt(attempt, printer, NOW);

        assertThatThrownBy(() -> job.failAttempt(attempt, printer, "late failure", NOW))
                .isInstanceOf(ConflictException.class)
                .extracting("code").isEqualTo("ATTEMPT_NOT_RUNNING");
        assertThat(attempt.getStatus()).isEqualTo(AttemptStatus.COMPLETED);
    }

    @Test
    void theBedCannotBeClearedWhilePrinting() {
        job.startAttempt(printer, NOW);

        assertThatThrownBy(() -> printer.clearBed(NOW))
                .isInstanceOf(ConflictException.class)
                .extracting("code").isEqualTo("PRINTER_BUSY");
    }
}
