package store.one3d.payments;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * The payment and payment_event tables, in plain SQL. Inserts use
 * {@code ON CONFLICT DO NOTHING} on the provider's ids: a second insert for
 * the same id waits for the first transaction and then reports "not inserted".
 */
@Component
class PaymentStore {

    private final JdbcClient jdbc;

    PaymentStore(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** Records a webhook receipt. False if this event id was already received. */
    boolean receiveEvent(String providerEventId, String eventType, String providerPaymentId,
                         String providerOrderId, Instant now) {
        return jdbc.sql("""
                        INSERT INTO payment_event (id, provider_event_id, event_type, provider_payment_id,
                                                   provider_order_id, received_at, outcome)
                        VALUES (?, ?, ?, ?, ?, ?, 'RECEIVED')
                        ON CONFLICT (provider_event_id) DO NOTHING
                        """)
                .params(UUID.randomUUID(), providerEventId, eventType, providerPaymentId, providerOrderId,
                        Timestamp.from(now))
                .update() == 1;
    }

    void setEventOutcome(String providerEventId, PaymentOutcome outcome) {
        jdbc.sql("UPDATE payment_event SET outcome = ? WHERE provider_event_id = ?")
                .params(outcome.name(), providerEventId).update();
    }

    /**
     * Records a captured payment with its decided outcome. False if this
     * payment id is already recorded (by another event or a reconciliation).
     */
    boolean insertPayment(PaymentGateway.ProviderPayment payment, CheckoutStore.Checkout checkout,
                          PaymentSource source, PaymentOutcome outcome, ReviewReason reason, Instant now) {
        return jdbc.sql("""
                        INSERT INTO payment (id, provider_payment_id, provider_order_id, checkout_id, order_id,
                                             amount_minor, currency, provider_created_at, recorded_at, source,
                                             outcome, review_reason)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        ON CONFLICT (provider_payment_id) DO NOTHING
                        """)
                .params(UUID.randomUUID(), payment.id(), payment.orderId(),
                        checkout == null ? null : checkout.id(), checkout == null ? null : checkout.orderId(),
                        payment.amountMinor(), payment.currency(), Timestamp.from(payment.createdAt()),
                        Timestamp.from(now), source.name(), outcome.name(), reason == null ? null : reason.name())
                .update() == 1;
    }

    List<PaymentView> list(boolean unresolvedReviewsOnly, int limit) {
        return jdbc.sql("""
                        SELECT id, provider_payment_id, provider_order_id, order_id, amount_minor, currency,
                               provider_created_at, recorded_at, source, outcome, review_reason, resolved_at,
                               resolution_note
                        FROM payment
                        WHERE NOT ? OR (outcome = 'NEEDS_REVIEW' AND resolved_at IS NULL)
                        ORDER BY recorded_at DESC LIMIT ?
                        """)
                .params(unresolvedReviewsOnly, limit)
                .query((row, n) -> new PaymentView(
                        row.getObject("id", UUID.class),
                        row.getString("provider_payment_id"),
                        row.getString("provider_order_id"),
                        row.getObject("order_id", UUID.class),
                        row.getLong("amount_minor"),
                        row.getString("currency"),
                        row.getTimestamp("provider_created_at").toInstant(),
                        row.getTimestamp("recorded_at").toInstant(),
                        row.getString("source"),
                        row.getString("outcome"),
                        row.getString("review_reason"),
                        row.getTimestamp("resolved_at") == null ? null : row.getTimestamp("resolved_at").toInstant(),
                        row.getString("resolution_note")))
                .list();
    }

    /** Marks an open review as investigated. False if there is no unresolved review with this id. */
    boolean resolve(UUID paymentId, String note, Instant now) {
        return jdbc.sql("""
                        UPDATE payment SET resolved_at = ?, resolution_note = ?
                        WHERE id = ? AND outcome = 'NEEDS_REVIEW' AND resolved_at IS NULL
                        """)
                .params(Timestamp.from(now), note, paymentId).update() == 1;
    }
}
