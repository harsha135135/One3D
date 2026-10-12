package store.one3d.support;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;

/**
 * Razorpay webhook bodies shaped like the documented payloads, and their
 * signatures computed here independently of the application's own code.
 */
public final class RazorpayEvents {

    /** The webhook secret the integration-test context is configured with (not a real secret). */
    public static final String TEST_WEBHOOK_SECRET = "test-webhook-secret";

    private RazorpayEvents() {
    }

    /** A payment.captured, payment.authorized, payment.failed or order.paid event for one payment. */
    public static String event(String type, String paymentId, String providerOrderId, long amountMinor,
                               String currency, String status, Instant paymentCreatedAt) {
        String order = type.equals("order.paid") ? """
                , "order": {"entity": {"id": "%s", "entity": "order", "amount": %d, "amount_paid": %d,
                   "amount_due": 0, "currency": "%s", "status": "paid"}}""".formatted(
                providerOrderId, amountMinor, amountMinor, currency) : "";
        return """
                {"entity": "event", "account_id": "acc_test", "event": "%s",
                 "contains": ["payment"%s],
                 "payload": {"payment": {"entity": {"id": "%s", "entity": "payment", "amount": %d,
                   "currency": "%s", "status": "%s", "order_id": "%s", "captured": %s, "method": "card",
                   "email": "customer@example.test", "contact": "+919000000000", "created_at": %d}}%s},
                 "created_at": %d}
                """.formatted(type, order.isEmpty() ? "" : ", \"order\"", paymentId, amountMinor, currency, status,
                providerOrderId, "captured".equals(status), paymentCreatedAt.getEpochSecond(), order,
                Instant.now().getEpochSecond());
    }

    public static String captured(String paymentId, String providerOrderId, long amountMinor) {
        return event("payment.captured", paymentId, providerOrderId, amountMinor, "INR", "captured", Instant.now());
    }

    public static String sign(String body, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(body.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public static String sign(String body) {
        return sign(body, TEST_WEBHOOK_SECRET);
    }

    public static String newEventId() {
        return "evt_test" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }

    public static String newPaymentId() {
        return "pay_test" + UUID.randomUUID().toString().replace("-", "").substring(0, 14);
    }
}
