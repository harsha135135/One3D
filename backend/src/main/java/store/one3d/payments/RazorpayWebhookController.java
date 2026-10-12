package store.one3d.payments;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import store.one3d.common.ApiException;
import store.one3d.common.NotFoundException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.Map;

/**
 * Razorpay's webhook. The only {@code /api} route open without a session or
 * CSRF token (SecurityConfig): it authenticates each request by its signature
 * instead. The signature is checked over the raw body bytes before anything is
 * parsed or stored. Neither the body nor the signature is logged.
 */
@RestController
class RazorpayWebhookController {

    static final String PATH = "/api/payments/razorpay/webhook";

    private final RazorpayProperties properties;
    private final RazorpayWebhooks webhooks;
    private final JsonMapper json;

    RazorpayWebhookController(RazorpayProperties properties, RazorpayWebhooks webhooks, JsonMapper json) {
        this.properties = properties;
        this.webhooks = webhooks;
        this.json = json;
    }

    @PostMapping(PATH)
    ResponseEntity<Map<String, String>> receive(
            @RequestHeader(name = "X-Razorpay-Signature", required = false) String signature,
            @RequestHeader(name = "X-Razorpay-Event-Id", required = false) String eventId,
            @RequestBody(required = false) byte[] body) {
        if (!properties.enabled()) {
            throw new NotFoundException("PAYMENTS_DISABLED", "Payments are not enabled.");
        }
        if (body == null || !WebhookSignature.matches(body, signature, properties.webhookSecret())) {
            throw new WebhookRejected("INVALID_SIGNATURE", "The webhook signature is missing or does not match.");
        }
        if (eventId == null || eventId.isBlank() || eventId.length() > 64) {
            throw new WebhookRejected("MISSING_EVENT_ID", "The webhook has no usable event id.");
        }
        JsonNode event;
        try {
            event = json.readTree(body);
        } catch (JacksonException e) {
            throw new WebhookRejected("MALFORMED_EVENT", "The webhook body is not JSON.");
        }
        PaymentOutcome outcome = webhooks.apply(eventId, event);
        return ResponseEntity.ok(Map.of("outcome", outcome.name()));
    }

    /** A webhook that is not accepted at all: nothing is recorded. */
    static class WebhookRejected extends ApiException {

        WebhookRejected(String code, String message) {
            super(HttpStatus.BAD_REQUEST, code, message);
        }
    }
}
