package store.one3d;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import store.one3d.support.TestcontainersConfiguration;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * V8 on a database that already has orders (as the local one did): the
 * timeline is filled from the old timestamps, and a demo confirmation is never
 * described as a payment. Uses its own database, migrated in two steps.
 */
class TimelineBackfillIT {

    @Test
    void existingOrdersGetTheirTimelineAndDemoConfirmationsSayNoPaymentWasTaken() throws Exception {
        try (PostgreSQLContainer postgres = new PostgreSQLContainer(
                DockerImageName.parse(TestcontainersConfiguration.POSTGRES_IMAGE))) {
            postgres.start();
            Flyway.configure().dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                    .target("7").load().migrate();
            try (Connection db = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(),
                    postgres.getPassword()); Statement sql = db.createStatement()) {
                sql.execute("""
                        INSERT INTO customer_order (id, customer_id, status, created_at, confirmed_at,
                                                    confirmation_source, ready_for_handoff_at)
                        VALUES ('0d3d0000-0000-4000-8000-00000000a001', '0d3d0000-0000-4000-8000-000000000201',
                                'READY_FOR_HANDOFF', '2026-10-01T10:00:00Z', '2026-10-01T10:05:00Z',
                                'DEMO_SIMULATED', '2026-10-02T09:00:00Z');
                        INSERT INTO customer_order (id, customer_id, status, created_at)
                        VALUES ('0d3d0000-0000-4000-8000-00000000a002', '0d3d0000-0000-4000-8000-000000000201',
                                'AWAITING_PAYMENT', '2026-10-03T10:00:00Z');
                        """);

                Flyway.configure().dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                        .load().migrate();

                assertThat(facts(sql, "a001")).containsExactly(
                        "PLACED|null",
                        "CONFIRMED|Confirmed by the demo shortcut; no payment was taken",
                        "READY_FOR_HANDOFF|null");
                assertThat(facts(sql, "a002")).containsExactly("PLACED|null");
                try (ResultSet window = sql.executeQuery("""
                        SELECT payment_window_ends_at = created_at + interval '72 hours' FROM customer_order
                        WHERE id = '0d3d0000-0000-4000-8000-00000000a002'""")) {
                    window.next();
                    assertThat(window.getBoolean(1)).isTrue();
                }
            }
        }
    }

    private static List<String> facts(Statement sql, String orderSuffix) throws Exception {
        List<String> facts = new ArrayList<>();
        try (ResultSet rows = sql.executeQuery("""
                SELECT kind, detail FROM order_fact WHERE order_id = '0d3d0000-0000-4000-8000-00000000%s'
                ORDER BY occurred_at""".formatted(orderSuffix))) {
            while (rows.next()) {
                facts.add(rows.getString("kind") + "|" + rows.getString("detail"));
            }
        }
        return facts;
    }
}
