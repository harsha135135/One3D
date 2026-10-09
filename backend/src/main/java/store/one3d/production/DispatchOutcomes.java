package store.one3d.production;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import store.one3d.background.PendingWorkStore;
import store.one3d.common.Timestamps;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Applies what the simulator reported, one short transaction per result. The
 * simulator itself is always called outside these transactions.
 */
@Service
class DispatchOutcomes {

    private static final Logger log = LoggerFactory.getLogger(DispatchOutcomes.class);
    private static final Set<AttemptStatus> AWAITING_START =
            Set.of(AttemptStatus.START_INTENT, AttemptStatus.RECONCILIATION_REQUIRED);

    private final PendingWorkStore work;
    private final PrinterCommandRepository commands;
    private final PrintAttemptRepository attempts;
    private final PrinterRepository printers;
    private final BedClearanceRepository clearances;
    private final Duration retryBackoff;

    DispatchOutcomes(PendingWorkStore work, PrinterCommandRepository commands, PrintAttemptRepository attempts,
                     PrinterRepository printers, BedClearanceRepository clearances,
                     @Value("${one3d.worker.retry-backoff}") Duration retryBackoff) {
        this.work = work;
        this.commands = commands;
        this.attempts = attempts;
        this.printers = printers;
        this.clearances = clearances;
        this.retryBackoff = retryBackoff;
    }

    /** The command to send for a claimed work row. */
    @Transactional(readOnly = true)
    public SimulatorClient.StartRequest startRequest(UUID commandId) {
        PrinterCommand command = commands.findById(commandId).orElseThrow();
        return new SimulatorClient.StartRequest(command.getId(), command.getPrinterId(), command.getAttemptId(),
                command.getJobId(), command.getPayloadHash(), command.getClearedAttemptId());
    }

    /**
     * Records a delivery result. The claim-token check runs first: if another
     * worker has taken over this row, nothing at all is changed.
     */
    @Transactional
    public boolean apply(PendingWorkStore.ClaimedWork claimed, DispatchResult result) {
        boolean stillOurs = result.kind() == DispatchResult.Kind.UNKNOWN
                ? work.retryLaterIfStillClaimed(claimed, result.reason(), backoffFor(claimed.attempts()))
                : work.completeIfStillClaimed(claimed);
        if (!stillOurs) {
            log.warn("Discarding late result {} for work {}: the claim was taken over", result.kind(), claimed.id());
            return false;
        }

        PrinterCommand command = commands.findById(claimed.subjectId()).orElseThrow();
        PrintAttempt attempt = attempts.findWithJob(command.getAttemptId()).orElseThrow();
        ProductionJob job = attempt.getJob();
        Instant now = Timestamps.now();
        if (!AWAITING_START.contains(attempt.getStatus())) {
            // Already settled (for example by an earlier delivery); only the command record changes.
            command.record(statusFor(result), now);
            return true;
        }
        switch (result.kind()) {
            case STARTED -> job.confirmStarted(attempt);
            case UNKNOWN -> job.startOutcomeUnknown(attempt);
            case REFUSED -> {
                Printer printer = printers.findForUpdate(attempt.getPrinterId()).orElseThrow();
                job.rejectBeforeStart(attempt, printer, "Refused by the printer: " + result.reason(), now);
                // Nothing printed, so the clearance the start used is still valid.
                clearances.findByConsumedByAttemptId(attempt.getId()).ifPresent(BedClearance::release);
            }
        }
        command.record(statusFor(result), now);
        return true;
    }

    /** Running attempts to poll (commands only; older attempts have none). */
    @Transactional(readOnly = true)
    public List<UUID> commandsOfRunningAttempts() {
        return attempts.commandsOfRunningAttempts();
    }

    /** Applies an execution end reported by the simulator, if the attempt is still running. */
    @Transactional
    public void recordExecutionEnd(UUID commandId, SimulatorClient.CommandReport report) {
        PrinterCommand command = commands.findById(commandId).orElseThrow();
        Optional<PrintAttempt> found = attempts.findWithJob(command.getAttemptId());
        if (found.isEmpty() || found.get().getStatus() != AttemptStatus.RUNNING) {
            return;
        }
        PrintAttempt attempt = found.get();
        Printer printer = printers.findForUpdate(attempt.getPrinterId()).orElseThrow();
        Instant now = Timestamps.now();
        if ("COMPLETED".equals(report.phase())) {
            attempt.getJob().completeAttempt(attempt, printer, now);
        } else if ("FAILED".equals(report.phase())) {
            String reason = report.failureReason() == null ? "Failed (no reason reported)" : report.failureReason();
            attempt.getJob().failAttempt(attempt, printer, reason, now);
        }
    }

    private Duration backoffFor(int attemptsSoFar) {
        // 1x, 2x, 4x ... the base backoff, capped at 32x.
        return retryBackoff.multipliedBy(1L << Math.min(attemptsSoFar - 1, 5));
    }

    private static CommandStatus statusFor(DispatchResult result) {
        return switch (result.kind()) {
            case STARTED -> CommandStatus.ACKNOWLEDGED;
            case REFUSED -> CommandStatus.REJECTED;
            case UNKNOWN -> CommandStatus.UNCERTAIN;
        };
    }
}
