package store.one3d.background;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Prometheus gauges for the work queue, read from {@code pending_work} when
 * metrics are scraped (at most once every few seconds):
 * <ul>
 *   <li>{@code one3d_work_items{kind, status}}: unfinished rows (PENDING,
 *       CLAIMED) and rows that need an operator (EXHAUSTED). DONE rows only
 *       grow and are left out.</li>
 *   <li>{@code one3d_work_oldest_age_seconds{kind}}: how long the oldest
 *       PENDING or CLAIMED row has existed; 0 when there is none. A value that
 *       keeps rising means work is stuck, for example while the simulator is
 *       unreachable.</li>
 * </ul>
 * Labels are the fixed {@link WorkKind} and status names only.
 */
@Component
class PendingWorkMetrics implements MeterBinder {

    private static final Logger log = LoggerFactory.getLogger(PendingWorkMetrics.class);
    private static final List<String> STATUSES = List.of("PENDING", "CLAIMED", "EXHAUSTED");

    /** One read of the queue; {@code readable} is false if the database could not be read (gauges show NaN). */
    private record Snapshot(Instant takenAt, boolean readable, Map<String, Double> items,
                            Map<WorkKind, Double> oldestAge) {

        double items(WorkKind kind, String status) {
            return readable ? items.getOrDefault(kind + "/" + status, 0.0) : Double.NaN;
        }

        double oldestAge(WorkKind kind) {
            return readable ? oldestAge.getOrDefault(kind, 0.0) : Double.NaN;
        }
    }

    private final JdbcClient jdbc;
    private final Duration refresh;
    private Snapshot snapshot;

    PendingWorkMetrics(JdbcClient jdbc, @Value("${one3d.metrics.work-refresh:5s}") Duration refresh) {
        this.jdbc = jdbc;
        this.refresh = refresh;
    }

    @Override
    public void bindTo(MeterRegistry registry) {
        for (WorkKind kind : WorkKind.values()) {
            for (String status : STATUSES) {
                Gauge.builder("one3d.work.items", this, m -> m.current().items(kind, status))
                        .description("Background work rows by kind and state (finished rows are left out)")
                        .tag("kind", kind.name())
                        .tag("status", status)
                        .register(registry);
            }
            Gauge.builder("one3d.work.oldest.age", this, m -> m.current().oldestAge(kind))
                    .description("Age of the oldest PENDING or CLAIMED work of this kind (0 when there is none)")
                    .baseUnit("seconds")
                    .tag("kind", kind.name())
                    .register(registry);
        }
    }

    private synchronized Snapshot current() {
        Instant now = Instant.now();
        if (snapshot == null || !snapshot.takenAt().plus(refresh).isAfter(now)) {
            snapshot = read(now);
        }
        return snapshot;
    }

    private Snapshot read(Instant now) {
        Map<String, Double> items = new HashMap<>();
        Map<WorkKind, Double> oldestAge = new HashMap<>();
        try {
            jdbc.sql("""
                            SELECT kind, status, count(*) AS n,
                                   extract(epoch FROM now() - min(created_at)) AS oldest_seconds
                            FROM pending_work
                            WHERE status IN ('PENDING', 'CLAIMED', 'EXHAUSTED')
                            GROUP BY kind, status
                            """)
                    .query(rs -> {
                        WorkKind kind = WorkKind.valueOf(rs.getString("kind"));
                        String status = rs.getString("status");
                        items.put(kind + "/" + status, (double) rs.getLong("n"));
                        if (!"EXHAUSTED".equals(status)) {
                            oldestAge.merge(kind, rs.getDouble("oldest_seconds"), Math::max);
                        }
                    });
        } catch (DataAccessException e) {
            log.warn("Could not read the work queue for metrics: {}", e.getClass().getSimpleName());
            return new Snapshot(now, false, Map.of(), Map.of());
        }
        return new Snapshot(now, true, items, oldestAge);
    }
}
