package store.one3d.payments;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import store.one3d.production.DispatchWorker;
import store.one3d.production.ExecutionObserver;
import store.one3d.support.ApiSteps;
import store.one3d.support.FakeRazorpay;
import store.one3d.support.PostgresIntegrationTest;
import store.one3d.support.RazorpayEvents;
import store.one3d.support.SimulatorRig;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Opening a test-mode checkout and checking with the provider whether it was
 * paid. The provider is {@link FakeRazorpay}; what the browser reports is never used.
 */
@PostgresIntegrationTest
class CheckoutIT {

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    FakeRazorpay razorpay;

    @Autowired
    DispatchWorker worker;

    @Autowired
    ExecutionObserver observer;

    @Autowired
    SimulatorRig simulator;

    private ApiSteps api;
    private String orderId;

    @BeforeEach
    void placeOrder() throws Exception {
        api = new ApiSteps(mvc, worker, observer, simulator);
        orderId = api.placeOrder();
    }

    @Test
    void aCheckoutIsForTheOrderTotalInTestModeAndIsReused() throws Exception {
        int createsBefore = razorpay.createCalls();

        String first = api.post("/api/orders/" + orderId + "/checkout")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.keyId").value("rzp_test_integration"))
                .andExpect(jsonPath("$.testMode").value(true))
                .andExpect(jsonPath("$.amountMinor").value(49_900))
                .andExpect(jsonPath("$.currency").value("INR"))
                .andReturn().getResponse().getContentAsString();
        String second = api.post("/api/orders/" + orderId + "/checkout")
                .andReturn().getResponse().getContentAsString();

        assertThat((String) JsonPath.read(second, "$.checkoutId")).isEqualTo(JsonPath.read(first, "$.checkoutId"));
        assertThat((String) JsonPath.read(second, "$.providerOrderId")).isEqualTo(JsonPath.read(first, "$.providerOrderId"));
        assertThat(razorpay.createCalls() - createsBefore).isOne();
        Instant expiresAt = Instant.parse(JsonPath.read(first, "$.expiresAt"));
        assertThat(expiresAt).isBefore(Instant.now().plus(Duration.ofMinutes(15)).plusSeconds(5));
    }

    @Test
    void aProviderOutageReturns503AndTheNextAttemptStartsAFreshCheckout() throws Exception {
        razorpay.failNextOrderCreations(1);

        api.post("/api/orders/" + orderId + "/checkout")
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("PAYMENT_PROVIDER_UNAVAILABLE"));
        assertThat(jdbc.sql("SELECT count(*) FROM checkout WHERE order_id = ?::uuid AND provider_order_id IS NULL")
                .param(orderId).query(Long.class).single()).isOne();

        api.post("/api/orders/" + orderId + "/checkout").andExpect(status().isOk());

        assertThat(jdbc.sql("SELECT status FROM checkout WHERE order_id = ?::uuid ORDER BY created_at")
                .param(orderId).query(String.class).list()).containsExactly("SUPERSEDED", "OPEN");
    }

    @Test
    void aCheckoutNeverOutlastsThePaymentWindow() throws Exception {
        jdbc.sql("UPDATE customer_order SET payment_window_ends_at = now() + interval '5 minutes' WHERE id = ?::uuid")
                .param(orderId).update();
        Instant windowEnd = jdbc.sql("SELECT payment_window_ends_at FROM customer_order WHERE id = ?::uuid")
                .param(orderId).query(java.sql.Timestamp.class).single().toInstant();

        String checkout = api.post("/api/orders/" + orderId + "/checkout").andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(Instant.parse(JsonPath.read(checkout, "$.expiresAt"))).isEqualTo(windowEnd);
    }

    @Test
    void aClosedPaymentWindowRefusesACheckout() throws Exception {
        jdbc.sql("UPDATE customer_order SET payment_window_ends_at = now() - interval '1 second' WHERE id = ?::uuid")
                .param(orderId).update();

        api.post("/api/orders/" + orderId + "/checkout")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PAYMENT_WINDOW_CLOSED"));
    }

    @Test
    void aPaymentCheckRecordsACapturedPaymentTheProviderReports() throws Exception {
        String providerOrderId = openCheckout();
        var payment = razorpay.addPayment(providerOrderId, 49_900, "captured");

        api.post("/api/orders/" + orderId + "/payment-check")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderStatus").value("CONFIRMED"))
                .andExpect(jsonPath("$.newlyRecorded").value(1));

        assertThat(jdbc.sql("SELECT source FROM payment WHERE provider_payment_id = ?").param(payment.id())
                .query(String.class).single()).isEqualTo("RECONCILIATION");
        // The webhook for the same payment, arriving later, changes nothing.
        api.webhook(RazorpayEvents.captured(payment.id(), providerOrderId, 49_900))
                .andExpect(jsonPath("$.outcome").value("ALREADY_RECORDED"));
    }

    @Test
    void aPaymentCheckDoesNotConfirmAnAuthorizedPayment() throws Exception {
        String providerOrderId = openCheckout();
        razorpay.addPayment(providerOrderId, 49_900, "authorized");

        api.post("/api/orders/" + orderId + "/payment-check")
                .andExpect(jsonPath("$.orderStatus").value("AWAITING_PAYMENT"))
                .andExpect(jsonPath("$.newlyRecorded").value(0));
    }

    @Test
    void aPaidOrderIsNotCheckedWithTheProviderAgain() throws Exception {
        api.pay(orderId);
        int queriesBefore = razorpay.paymentQueries();

        api.post("/api/orders/" + orderId + "/payment-check")
                .andExpect(jsonPath("$.orderStatus").value("CONFIRMED"))
                .andExpect(jsonPath("$.newlyRecorded").value(0));

        assertThat(razorpay.paymentQueries()).isEqualTo(queriesBefore);
    }

    private String openCheckout() throws Exception {
        return JsonPath.read(api.post("/api/orders/" + orderId + "/checkout").andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(), "$.providerOrderId");
    }
}
