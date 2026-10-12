package store.one3d.orders;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * The order_fact table: the customer's timeline, in plain SQL. Writes join the
 * caller's transaction, so a fact exists exactly when the change it describes
 * does. Recording a milestone twice keeps the first one.
 */
@Component
class OrderFacts {

    private final JdbcClient jdbc;

    OrderFacts(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    void record(UUID orderId, OrderFactKind kind, Instant occurredAt, String detail) {
        jdbc.sql("""
                        INSERT INTO order_fact (id, order_id, kind, occurred_at, detail)
                        VALUES (?, ?, ?, ?, ?)
                        ON CONFLICT DO NOTHING
                        """)
                .params(UUID.randomUUID(), orderId, kind.name(), java.sql.Timestamp.from(occurredAt),
                        detail == null || detail.length() <= 200 ? detail : detail.substring(0, 200))
                .update();
    }

    List<TimelineEntry> of(UUID orderId) {
        return jdbc.sql("SELECT kind, occurred_at, detail FROM order_fact WHERE order_id = ? ORDER BY occurred_at, kind")
                .param(orderId)
                .query((row, n) -> new TimelineEntry(
                        OrderFactKind.valueOf(row.getString("kind")),
                        row.getTimestamp("occurred_at").toInstant(),
                        row.getString("detail")))
                .list();
    }
}
