package store.one3d.production;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import store.one3d.background.BackgroundJob;

import java.util.UUID;

/**
 * Polls the simulator for running attempts and records completions and
 * failures. If the simulator cannot be reached, nothing changes and the next
 * poll tries again.
 */
@Component
public class ExecutionObserver implements BackgroundJob {

    private static final Logger log = LoggerFactory.getLogger(ExecutionObserver.class);

    private final DispatchOutcomes outcomes;
    private final SimulatorClient simulator;
    private final ProductionMetrics metrics;

    ExecutionObserver(DispatchOutcomes outcomes, SimulatorClient simulator, ProductionMetrics metrics) {
        this.outcomes = outcomes;
        this.simulator = simulator;
        this.metrics = metrics;
    }

    @Override
    public int runOnce() {
        return observeOnce();
    }

    /** Returns the number of running attempts checked. */
    public int observeOnce() {
        int checked = 0;
        for (UUID commandId : outcomes.commandsOfRunningAttempts()) {
            checked++;
            try (MDC.MDCCloseable command = MDC.putCloseable("command_id", commandId.toString())) {
                simulator.findCommand(commandId)
                        .filter(report -> !"RUNNING".equals(report.phase()))
                        .ifPresent(report -> {
                            if (outcomes.recordExecutionEnd(commandId, report)) {
                                metrics.attemptEnded("COMPLETED".equals(report.phase()));
                            }
                        });
            } catch (SimulatorUnavailableException e) {
                metrics.simulatorUnreachable(ProductionMetrics.SimulatorOperation.OBSERVE);
                log.warn("Could not observe command {}: {}", commandId, e.getMessage());
            }
        }
        return checked;
    }
}
