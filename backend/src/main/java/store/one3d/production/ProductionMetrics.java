package store.one3d.production;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.Map;

/**
 * Prometheus metrics for printing (labels are fixed names only, never ids):
 * <ul>
 *   <li>{@code one3d_dispatch_results_total{result}}: start deliveries by
 *       result: started, refused, or unknown (no answer from the simulator,
 *       so the attempt waits for reconciliation);</li>
 *   <li>{@code one3d_attempts_ended_total{result}}: prints the simulator
 *       reported as completed or failed;</li>
 *   <li>{@code one3d_simulator_unreachable_total{operation}}: calls to the
 *       simulator that got no answer, while delivering a start or observing a
 *       running print;</li>
 *   <li>{@code one3d_attempts_awaiting_reconciliation}: attempts whose start
 *       is uncertain right now (RECONCILIATION_REQUIRED).</li>
 * </ul>
 */
@Component
public class ProductionMetrics {

    enum SimulatorOperation { DISPATCH, OBSERVE }

    private final Map<DispatchResult.Kind, Counter> dispatchResults = new EnumMap<>(DispatchResult.Kind.class);
    private final Counter completed;
    private final Counter failed;
    private final Map<SimulatorOperation, Counter> unreachable = new EnumMap<>(SimulatorOperation.class);

    ProductionMetrics(MeterRegistry registry, JdbcClient jdbc) {
        for (DispatchResult.Kind kind : DispatchResult.Kind.values()) {
            dispatchResults.put(kind, Counter.builder("one3d.dispatch.results")
                    .description("Start deliveries to the simulator by result")
                    .tag("result", kind.name().toLowerCase())
                    .register(registry));
        }
        completed = ended(registry, "completed");
        failed = ended(registry, "failed");
        for (SimulatorOperation operation : SimulatorOperation.values()) {
            unreachable.put(operation, Counter.builder("one3d.simulator.unreachable")
                    .description("Simulator calls that got no answer")
                    .tag("operation", operation.name().toLowerCase())
                    .register(registry));
        }
        Gauge.builder("one3d.attempts.awaiting.reconciliation", jdbc, ProductionMetrics::awaitingReconciliation)
                .description("Attempts whose start is uncertain until the simulator's journal answers")
                .register(registry);
    }

    private static Counter ended(MeterRegistry registry, String result) {
        return Counter.builder("one3d.attempts.ended")
                .description("Prints the simulator reported as finished, by result")
                .tag("result", result)
                .register(registry);
    }

    private static double awaitingReconciliation(JdbcClient jdbc) {
        try {
            return jdbc.sql("SELECT count(*) FROM print_attempt WHERE status = 'RECONCILIATION_REQUIRED'")
                    .query(Long.class).single();
        } catch (RuntimeException e) {
            return Double.NaN;
        }
    }

    void dispatched(DispatchResult.Kind result) {
        dispatchResults.get(result).increment();
    }

    void attemptEnded(boolean succeeded) {
        (succeeded ? completed : failed).increment();
    }

    void simulatorUnreachable(SimulatorOperation operation) {
        unreachable.get(operation).increment();
    }
}
