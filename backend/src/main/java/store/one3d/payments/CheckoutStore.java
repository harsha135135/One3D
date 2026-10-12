package store.one3d.payments;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** The checkout table, in plain SQL. Every method joins the caller's transaction. */
@Component
class CheckoutStore {

    /** One payment attempt for an order: our id, the provider's order id once known, the amount and the window. */
    record Checkout(UUID id, UUID orderId, String providerOrderId, long amountMinor, String currency, String status,
                    Instant createdAt, Instant expiresAt) {
    }

    private static final String COLUMNS =
            "id, order_id, provider_order_id, amount_minor, currency, status, created_at, expires_at";

    private final JdbcClient jdbc;

    CheckoutStore(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    Optional<Checkout> openFor(UUID orderId) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM checkout WHERE order_id = ? AND status = 'OPEN'")
                .param(orderId).query(CheckoutStore::row).optional();
    }

    void insert(Checkout checkout) {
        jdbc.sql("INSERT INTO checkout (" + COLUMNS + ") VALUES (?, ?, ?, ?, ?, ?, ?, ?)")
                .params(checkout.id(), checkout.orderId(), checkout.providerOrderId(), checkout.amountMinor(),
                        checkout.currency(), checkout.status(), Timestamp.from(checkout.createdAt()),
                        Timestamp.from(checkout.expiresAt()))
                .update();
    }

    void supersede(UUID checkoutId) {
        jdbc.sql("UPDATE checkout SET status = 'SUPERSEDED' WHERE id = ? AND status = 'OPEN'").param(checkoutId).update();
    }

    /**
     * Records the provider's order id. Also on a checkout superseded meanwhile:
     * the customer may still pay against it, and the payment must be matched.
     */
    boolean attachProviderOrder(UUID checkoutId, String providerOrderId) {
        return jdbc.sql("""
                        UPDATE checkout SET provider_order_id = ?
                        WHERE id = ? AND provider_order_id IS NULL
                        """)
                .params(providerOrderId, checkoutId).update() == 1;
    }

    /** Which order a provider order belongs to, without reading or locking the order itself. */
    Optional<UUID> orderIdOf(String providerOrderId) {
        return jdbc.sql("SELECT order_id FROM checkout WHERE provider_order_id = ?")
                .param(providerOrderId).query(UUID.class).optional();
    }

    /** Locks the checkout row; callers lock the order first. */
    Checkout lockByProviderOrder(String providerOrderId) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM checkout WHERE provider_order_id = ? FOR UPDATE")
                .param(providerOrderId).query(CheckoutStore::row).single();
    }

    /** The paying checkout becomes PAID; any other open checkout of the order is closed. */
    void markPaid(Checkout paid) {
        jdbc.sql("UPDATE checkout SET status = 'SUPERSEDED' WHERE order_id = ? AND status = 'OPEN' AND id <> ?")
                .params(paid.orderId(), paid.id()).update();
        jdbc.sql("UPDATE checkout SET status = 'PAID' WHERE id = ?").param(paid.id()).update();
    }

    /** The order's provider orders, newest first, at most {@code limit}: what a payment check asks the provider about. */
    List<String> providerOrdersOf(UUID orderId, int limit) {
        return jdbc.sql("""
                        SELECT provider_order_id FROM checkout
                        WHERE order_id = ? AND provider_order_id IS NOT NULL
                        ORDER BY created_at DESC LIMIT ?
                        """)
                .params(orderId, limit).query(String.class).list();
    }

    private static Checkout row(ResultSet row, int n) throws SQLException {
        return new Checkout(row.getObject("id", UUID.class), row.getObject("order_id", UUID.class),
                row.getString("provider_order_id"), row.getLong("amount_minor"), row.getString("currency"),
                row.getString("status"), row.getTimestamp("created_at").toInstant(),
                row.getTimestamp("expires_at").toInstant());
    }
}
