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
    void startingRecordsTheIntentAndReservesThePrinterAndBed() {
        PrintAttempt attempt = job.startAttempt(printer, NOW);

        assertThat(job.getStatus()).isEqualTo(JobStatus.PRINTING);
        assertThat(attempt.getAttemptNumber()).isEqualTo(1);
        assertThat(attempt.getStatus()).isEqualTo(AttemptStatus.START_INTENT);
        assertThat(printer.getCurrentAttemptId()).isEqualTo(attempt.getId());
        assertThat(printer.getBedState()).isEqualTo(BedState.OCCUPIED);
        assertThat(printer.getBedCycleAttemptId()).isEqualTo(attempt.getId());
    }

    @Test
    void anUncertainStartKeepsThePrinterAndJobUntilConfirmed() {
        PrintAttempt attempt = job.startAttempt(printer, NOW);

        job.startOutcomeUnknown(attempt);

        assertThat(attempt.getStatus()).isEqualTo(AttemptStatus.RECONCILIATION_REQUIRED);
        assertThat(job.getStatus()).isEqualTo(JobStatus.PRINTING);
        assertThat(printer.getCurrentAttemptId()).isEqualTo(attempt.getId());
        ProductionJob other = ProductionJob.release(UUID.randomUUID(), UUID.randomUUID(), NOW);
        assertThatThrownBy(() -> other.startAttempt(printer, NOW))
                .isInstanceOf(ConflictException.class)
                .extracting("code").isEqualTo("PRINTER_BUSY");

        job.confirmStarted(attempt);
        assertThat(attempt.getStatus()).isEqualTo(AttemptStatus.RUNNING);
    }

    @Test
    void aRefusedStartIsUndoneAndDoesNotCountTowardTheLimit() {
        PrintAttempt refused = job.startAttempt(printer, NOW);

        job.rejectBeforeStart(refused, printer, "Refused by the printer: REJECTED_BUSY", NOW);

        assertThat(refused.getStatus()).isEqualTo(AttemptStatus.REJECTED_BEFORE_START);
        assertThat(job.getStatus()).isEqualTo(JobStatus.RELEASED);
        assertThat(printer.getCurrentAttemptId()).isNull();
        assertThat(printer.getBedState()).isEqualTo(BedState.CLEAR);
        assertThat(printer.getBedCycleAttemptId()).isNull();

        // Three attempts that did reach the printer are still allowed.
        for (int i = 0; i < ProductionJob.MAX_ATTEMPTS; i++) {
            failAndClear(job.startAttempt(printer, NOW));
        }
        assertThat(job.getAttempts()).extracting(PrintAttempt::getAttemptNumber).containsExactly(1, 2, 3, 4);
        assertThatThrownBy(() -> job.startAttempt(printer, NOW))
                .isInstanceOf(ConflictException.class)
                .extracting("code").isEqualTo("ATTEMPT_LIMIT_REACHED");
    }

    @Test
    void completionNeedsQcAndLeavesThePrintOnTheBed() {
        PrintAttempt attempt = running(job.startAttempt(printer, NOW));

        job.completeAttempt(attempt, printer, NOW);

        assertThat(job.getStatus()).isEqualTo(JobStatus.AWAITING_QC);
        assertThat(attempt.getStatus()).isEqualTo(AttemptStatus.COMPLETED);
        assertThat(printer.getCurrentAttemptId()).isNull();
        assertThat(printer.getBedState()).isEqualTo(BedState.OCCUPIED);
        assertThat(printer.getBedCycleAttemptId()).isEqualTo(attempt.getId());
    }

    @Test
    void failureKeepsTheAttemptAndARetryIsAttemptTwo() {
        PrintAttempt first = running(job.startAttempt(printer, NOW));
        job.failAttempt(first, printer, "Nozzle clog", NOW);

        assertThat(job.getStatus()).isEqualTo(JobStatus.RELEASED);
        assertThat(first.getStatus()).isEqualTo(AttemptStatus.FAILED);
        assertThat(first.getFailureReason()).isEqualTo("Nozzle clog");
        assertThat(printer.getBedState()).isEqualTo(BedState.OCCUPIED);

        printer.clearBed(first.getId(), NOW);
        PrintAttempt second = job.startAttempt(printer, NOW);

        assertThat(second.getAttemptNumber()).isEqualTo(2);
        assertThat(job.getAttempts()).extracting(PrintAttempt::getStatus)
                .containsExactly(AttemptStatus.FAILED, AttemptStatus.START_INTENT);
    }

    @Test
    void anOccupiedBedBlocksTheNextStartAndLeavesTheJobUnchanged() {
        PrintAttempt first = running(job.startAttempt(printer, NOW));
        job.failAttempt(first, printer, "Warping", NOW);

        assertThatThrownBy(() -> job.startAttempt(printer, NOW))
                .isInstanceOf(ConflictException.class)
                .extracting("code").isEqualTo("BED_NOT_CLEAR");
        assertThat(job.getStatus()).isEqualTo(JobStatus.RELEASED);
        assertThat(job.getAttempts()).hasSize(1);
    }

    @Test
    void aClearanceMustNameThePrintOnTheBed() {
        PrintAttempt first = running(job.startAttempt(printer, NOW));
        job.failAttempt(first, printer, "Warping", NOW);
        printer.clearBed(first.getId(), NOW);
        PrintAttempt second = running(job.startAttempt(printer, NOW));
        job.failAttempt(second, printer, "Spaghetti", NOW);

        // A delayed clearance for the first print must not clear the second.
        assertThatThrownBy(() -> printer.clearBed(first.getId(), NOW))
                .isInstanceOf(ConflictException.class)
                .extracting("code").isEqualTo("STALE_CLEARANCE");
        assertThat(printer.getBedState()).isEqualTo(BedState.OCCUPIED);
        assertThat(printer.getBedCycleAttemptId()).isEqualTo(second.getId());
    }

    @Test
    void aFourthCountedAttemptIsRejectedAndChangesNothing() {
        for (int i = 0; i < ProductionJob.MAX_ATTEMPTS; i++) {
            failAndClear(job.startAttempt(printer, NOW));
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
        PrintAttempt attempt = running(job.startAttempt(printer, NOW));
        job.completeAttempt(attempt, printer, NOW);

        job.recordQc(attempt, QcResult.PASSED, null, NOW);

        assertThat(job.getStatus()).isEqualTo(JobStatus.ACCEPTED);
        assertThat(job.getAcceptedAt()).isEqualTo(NOW);
        job.requireAccepted();
    }

    @Test
    void failingQcRequiresAnotherAttemptAndBlocksHandoff() {
        PrintAttempt attempt = running(job.startAttempt(printer, NOW));
        job.completeAttempt(attempt, printer, NOW);

        job.recordQc(attempt, QcResult.FAILED, "Layer shift", NOW);

        assertThat(job.getStatus()).isEqualTo(JobStatus.RELEASED);
        assertThatThrownBy(job::requireAccepted)
                .isInstanceOf(ConflictException.class)
                .extracting("code").isEqualTo("JOB_NOT_ACCEPTED");
    }

    @Test
    void aRunningAttemptCannotBeInspected() {
        PrintAttempt attempt = running(job.startAttempt(printer, NOW));

        assertThatThrownBy(() -> job.recordQc(attempt, QcResult.PASSED, null, NOW))
                .isInstanceOf(ConflictException.class)
                .extracting("code").isEqualTo("JOB_NOT_AWAITING_QC");
    }

    @Test
    void anUnconfirmedStartCannotComplete() {
        PrintAttempt attempt = job.startAttempt(printer, NOW);

        assertThatThrownBy(() -> job.completeAttempt(attempt, printer, NOW))
                .isInstanceOf(ConflictException.class)
                .extracting("code").isEqualTo("ATTEMPT_NOT_STARTED");
    }

    @Test
    void anEndedAttemptCannotEndAgain() {
        PrintAttempt attempt = running(job.startAttempt(printer, NOW));
        job.completeAttempt(attempt, printer, NOW);

        assertThatThrownBy(() -> job.failAttempt(attempt, printer, "late failure", NOW))
                .isInstanceOf(ConflictException.class)
                .extracting("code").isEqualTo("ATTEMPT_NOT_RUNNING");
        assertThat(attempt.getStatus()).isEqualTo(AttemptStatus.COMPLETED);
    }

    @Test
    void theBedCannotBeClearedWhilePrinting() {
        PrintAttempt attempt = running(job.startAttempt(printer, NOW));

        assertThatThrownBy(() -> printer.clearBed(attempt.getId(), NOW))
                .isInstanceOf(ConflictException.class)
                .extracting("code").isEqualTo("PRINTER_BUSY");
    }

    private PrintAttempt running(PrintAttempt attempt) {
        job.confirmStarted(attempt);
        return attempt;
    }

    private void failAndClear(PrintAttempt attempt) {
        job.confirmStarted(attempt);
        job.failAttempt(attempt, printer, "Warping", NOW);
        printer.clearBed(attempt.getId(), NOW);
    }
}
