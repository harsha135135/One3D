package store.one3d.payments;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import store.one3d.common.Timestamps;
import tools.jackson.databind.JsonNode;

import java.util.Set;

/**
 * Applies one signed Razorpay webhook event in one transaction. The receipt
 * (unique provider event id) and the event's effect commit together, and the
 * controller answers 2xx only after that commit; if anything fails, nothing is
 * kept and Razorpay delivers the event again.
 */
@Service
class RazorpayWebhooks {

    private static final Logger log = LoggerFactory.getLogger(RazorpayWebhooks.class);
    /** Events whose payment entity can report a capture. Anything else changes nothing. */
    private static final Set<String> CAPTURE_EVENTS = Set.of("payment.captured", "order.paid");

    private final PaymentStore payments;
    private final PaymentRecorder recorder;

    RazorpayWebhooks(PaymentStore payments, PaymentRecorder recorder) {
        this.payments = payments;
        this.recorder = recorder;
    }

    @Transactional
    public PaymentOutcome apply(String eventId, JsonNode event) {
        String type = event.path("event").asString("");
        JsonNode paymentEntity = event.path("payload").path("payment").path("entity");
        String paymentId = paymentEntity.path("id").asString(null);
        String providerOrderId = paymentEntity.path("order_id").asString(null);
        if (!payments.receiveEvent(eventId, type.isEmpty() ? "unknown" : type, paymentId, providerOrderId,
                Timestamps.now())) {
            log.info("Webhook event {} ({}) already received", eventId, type);
            return PaymentOutcome.ALREADY_RECORDED;
        }
        PaymentOutcome outcome = outcomeOf(type, paymentEntity);
        payments.setEventOutcome(eventId, outcome);
        log.info("Webhook event {} ({}) for payment {}: {}", eventId, type, paymentId, outcome);
        return outcome;
    }

    private PaymentOutcome outcomeOf(String type, JsonNode paymentEntity) {
        if (!CAPTURE_EVENTS.contains(type)) {
            return PaymentOutcome.IGNORED; // payment.authorized, payment.failed, ...: never confirm an order
        }
        PaymentGateway.ProviderPayment payment;
        try {
            payment = RazorpayGateway.RazorpayPayments.from(paymentEntity);
        } catch (PaymentProviderException e) {
            log.warn("Webhook {} event without a usable payment entity: {}", type, e.getMessage());
            return PaymentOutcome.IGNORED;
        }
        if (!payment.captured()) {
            return PaymentOutcome.IGNORED;
        }
        return recorder.record(payment, PaymentSource.WEBHOOK);
    }
}
