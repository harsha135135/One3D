package store.one3d.payments;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The Razorpay adapter against a local HTTP stub: what it sends and how it reads answers. */
class RazorpayGatewayTest {

    private final JsonMapper json = JsonMapper.builder().build();
    private final AtomicReference<String> lastMethod = new AtomicReference<>();
    private final AtomicReference<String> lastPath = new AtomicReference<>();
    private final AtomicReference<String> lastAuthorization = new AtomicReference<>();
    private final AtomicReference<String> lastBody = new AtomicReference<>();
    private volatile int status = 200;
    private volatile String response = "{}";
    private HttpServer server;
    private RazorpayGateway gateway;

    @BeforeEach
    void startStub() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            lastMethod.set(exchange.getRequestMethod());
            lastPath.set(exchange.getRequestURI().getPath());
            lastAuthorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            lastBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] body = response.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        RazorpayProperties properties = new RazorpayProperties(true, "rzp_test_unit", "unit-secret", "unit-webhook",
                "http://127.0.0.1:" + server.getAddress().getPort(), Duration.ofSeconds(1), Duration.ofSeconds(2),
                Duration.ofMinutes(15));
        gateway = new RazorpayGateway(properties, json);
    }

    @AfterEach
    void stopStub() {
        server.stop(0);
    }

    @Test
    void createsAnOrderThatCapturesAutomaticallyWithBasicAuth() throws Exception {
        response = """
                {"id": "order_ABC123", "entity": "order", "amount": 49900, "currency": "INR", "status": "created"}""";

        PaymentGateway.ProviderOrder order = gateway.createOrder("checkout-1", 49_900, "INR");

        assertThat(order).isEqualTo(new PaymentGateway.ProviderOrder("order_ABC123", 49_900, "INR"));
        assertThat(lastMethod.get()).isEqualTo("POST");
        assertThat(lastPath.get()).isEqualTo("/v1/orders");
        assertThat(lastAuthorization.get()).isEqualTo("Basic " + Base64.getEncoder()
                .encodeToString("rzp_test_unit:unit-secret".getBytes(StandardCharsets.UTF_8)));
        JsonNode sent = json.readTree(lastBody.get());
        assertThat(sent.path("amount").asLong()).isEqualTo(49_900);
        assertThat(sent.path("currency").asString()).isEqualTo("INR");
        assertThat(sent.path("receipt").asString()).isEqualTo("checkout-1");
        assertThat(sent.path("payment").path("capture").asString()).isEqualTo("automatic");
        assertThat(sent.path("payment").path("capture_options").path("automatic_expiry_period").asInt()).isEqualTo(12);
    }

    @Test
    void readsTheOrdersPayments() throws Exception {
        response = """
                {"entity": "collection", "count": 2, "items": [
                  {"id": "pay_1", "order_id": "order_ABC123", "amount": 49900, "currency": "INR",
                   "status": "captured", "created_at": 1760000000, "email": "x@example.test"},
                  {"id": "pay_2", "order_id": "order_ABC123", "amount": 49900, "currency": "INR",
                   "status": "failed", "created_at": 1760000100}]}""";

        List<PaymentGateway.ProviderPayment> payments = gateway.paymentsOf("order_ABC123");

        assertThat(lastPath.get()).isEqualTo("/v1/orders/order_ABC123/payments");
        assertThat(payments).extracting(PaymentGateway.ProviderPayment::id).containsExactly("pay_1", "pay_2");
        assertThat(payments.get(0).captured()).isTrue();
        assertThat(payments.get(1).captured()).isFalse();
        assertThat(payments.get(0).createdAt()).isEqualTo(Instant.ofEpochSecond(1_760_000_000));
    }

    @Test
    void anErrorAnswerBecomesAProviderExceptionWithoutTheBody() {
        status = 401;
        response = """
                {"error": {"code": "BAD_REQUEST_ERROR", "description": "Authentication failed for rzp_test_unit"}}""";

        assertThatThrownBy(() -> gateway.createOrder("checkout-2", 100, "INR"))
                .isInstanceOf(PaymentProviderException.class)
                .hasMessageContaining("HTTP 401 BAD_REQUEST_ERROR")
                .hasMessageNotContaining("Authentication failed");
    }

    @Test
    void onlyRazorpayOrderIdsAreSentInAPath() {
        assertThatThrownBy(() -> gateway.paymentsOf("../v1/payments")).isInstanceOf(PaymentProviderException.class);
    }
}
