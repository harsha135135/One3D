package store.one3d.production;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import store.one3d.background.BackgroundJob;
import store.one3d.background.PendingWorkStore;
import store.one3d.background.WorkKind;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

/**
 * Delivers start commands from the work queue to the simulator.
 *
 * <p>For each claimed row it first asks the simulator's journal whether the
 * command was already received (for example when an earlier reply was lost),
 * and sends it only if not. Sending the same command id again would also be
 * safe. A missing answer leaves the attempt RECONCILIATION_REQUIRED, holding
 * its printer, and the row is retried with backoff; it never becomes a new attempt.
 */
@Component
public class DispatchWorker implements BackgroundJob {

    private static final Logger log = LoggerFactory.getLogger(DispatchWorker.class);

    private final PendingWorkStore work;
    private final DispatchOutcomes outcomes;
    private final SimulatorClient simulator;
    private final ProductionMetrics metrics;
    private final int batchSize;
    private final Duration lease;

    DispatchWorker(PendingWorkStore work, DispatchOutcomes outcomes, SimulatorClient simulator,
                   ProductionMetrics metrics,
                   @Value("${one3d.worker.batch-size}") int batchSize,
                   @Value("${one3d.worker.lease}") Duration lease) {
        this.work = work;
        this.outcomes = outcomes;
        this.simulator = simulator;
        this.metrics = metrics;
        this.batchSize = batchSize;
        this.lease = lease;
    }

    /** Claims due start deliveries and processes them. Returns the number of rows claimed. */
    @Override
    public int runOnce() {
        List<PendingWorkStore.ClaimedWork> claimed = work.claim(WorkKind.DISPATCH_START, batchSize, lease);
        claimed.forEach(this::process);
        return claimed.size();
    }

    void process(PendingWorkStore.ClaimedWork claimed) {
        SimulatorClient.StartRequest request = outcomes.startRequest(claimed.subjectId());
        // Every log line about this delivery names the command, attempt and job.
        try (MDC.MDCCloseable command = MDC.putCloseable("command_id", request.commandId().toString());
             MDC.MDCCloseable attempt = MDC.putCloseable("attempt_id", request.attemptId().toString());
             MDC.MDCCloseable job = MDC.putCloseable("job_id", request.jobId().toString())) {
            DispatchResult result = deliver(request);
            if (outcomes.apply(claimed, result)) {
                metrics.dispatched(result.kind());
            }
        }
    }

    private DispatchResult deliver(SimulatorClient.StartRequest request) {
        try {
            Optional<SimulatorClient.CommandReport> known = simulator.findCommand(request.commandId());
            return DispatchResult.from(known.isPresent() ? known.get() : simulator.start(request));
        } catch (SimulatorUnavailableException e) {
            metrics.simulatorUnreachable(ProductionMetrics.SimulatorOperation.DISPATCH);
            log.warn("Start of attempt {} not confirmed (command {}): {}",
                    request.attemptId(), request.commandId(), e.getMessage());
            return DispatchResult.unknown(e.getMessage());
        }
    }
}
