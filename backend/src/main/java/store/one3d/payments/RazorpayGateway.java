package store.one3d.payments;

import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Razorpay's Orders API over plain HTTPS ({@code java.net.http}, no SDK),
 * authenticated with the test-mode key id and secret. Requests and responses
 * are never logged: they carry the key and customer details.
 */
@Component
class RazorpayGateway implements PaymentGateway {

    /** Razorpay's minimum time before an authorized payment is captured automatically. */
    private static final int AUTOMATIC_CAPTURE_MINUTES = 12;

    private final RazorpayProperties properties;
    private final JsonMapper json;
    private final HttpClient http;

    RazorpayGateway(RazorpayProperties properties, JsonMapper json) {
        this.properties = properties;
        this.json = json;
        this.http = HttpClient.newBuilder().connectTimeout(properties.connectTimeout()).build();
    }

    @Override
    public ProviderOrder createOrder(String receipt, long amountMinor, String currency) throws PaymentProviderException {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("amount", amountMinor);
        body.put("currency", currency);
        body.put("receipt", receipt);
        // Capture automatically, so a successful checkout ends as "captured";
        // an order is confirmed only by a captured payment.
        body.put("payment", Map.of("capture", "automatic",
                "capture_options", Map.of("automatic_expiry_period", AUTOMATIC_CAPTURE_MINUTES)));
        JsonNode order = send(HttpRequest.newBuilder(uri("/v1/orders"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(write(body))), "create order");
        return new ProviderOrder(text(order, "id"), order.path("amount").asLong(), text(order, "currency"));
    }

    @Override
    public List<ProviderPayment> paymentsOf(String providerOrderId) throws PaymentProviderException {
        if (!providerOrderId.matches("order_[A-Za-z0-9]+")) {
            throw new PaymentProviderException("Not a Razorpay order id");
        }
        JsonNode page = send(HttpRequest.newBuilder(uri("/v1/orders/" + providerOrderId + "/payments")).GET(),
                "list payments");
        List<ProviderPayment> payments = new ArrayList<>();
        for (JsonNode payment : page.path("items")) {
            payments.add(RazorpayPayments.from(payment));
        }
        return payments;
    }

    private JsonNode send(HttpRequest.Builder request, String what) throws PaymentProviderException {
        String credentials = properties.keyId() + ":" + properties.keySecret();
        request.header("Authorization", "Basic "
                        + Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8)))
                .header("Accept", "application/json")
                .timeout(properties.requestTimeout());
        HttpResponse<String> response;
        try {
            response = http.send(request.build(), HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new PaymentProviderException("Razorpay " + what + " failed: " + e.getClass().getSimpleName(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new PaymentProviderException("Interrupted during Razorpay " + what, e);
        }
        if (response.statusCode() / 100 != 2) {
            // Only the status and Razorpay's error code; the body may echo request details.
            throw new PaymentProviderException("Razorpay " + what + " returned HTTP " + response.statusCode()
                    + " " + errorCode(response.body()));
        }
        try {
            return json.readTree(response.body());
        } catch (JacksonException e) {
            throw new PaymentProviderException("Razorpay " + what + " returned an unreadable body", e);
        }
    }

    private String errorCode(String body) {
        try {
            return json.readTree(body).path("error").path("code").asString("");
        } catch (JacksonException e) {
            return "";
        }
    }

    private String write(Object body) {
        try {
            return json.writeValueAsString(body);
        } catch (JacksonException e) {
            throw new IllegalStateException("Cannot serialize a Razorpay request", e);
        }
    }

    private URI uri(String path) {
        return URI.create(properties.baseUrl() + path);
    }

    private static String text(JsonNode node, String field) throws PaymentProviderException {
        String value = node.path(field).asString("");
        if (value.isEmpty()) {
            throw new PaymentProviderException("Razorpay response has no " + field);
        }
        return value;
    }

    /** Reads a Razorpay payment entity (API list item or webhook {@code payload.payment.entity}). */
    static final class RazorpayPayments {

        private RazorpayPayments() {
        }

        static ProviderPayment from(JsonNode payment) throws PaymentProviderException {
            String id = payment.path("id").asString("");
            String orderId = payment.path("order_id").asString("");
            String currency = payment.path("currency").asString("");
            if (id.isEmpty() || orderId.isEmpty() || currency.isEmpty() || !payment.path("amount").isIntegralNumber()
                    || !payment.path("created_at").isIntegralNumber()) {
                throw new PaymentProviderException("Payment entity is missing id, order_id, amount, currency or created_at");
            }
            return new ProviderPayment(id, orderId, payment.path("amount").asLong(), currency,
                    payment.path("status").asString(""), Instant.ofEpochSecond(payment.path("created_at").asLong()));
        }
    }
}
