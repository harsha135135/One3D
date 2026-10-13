package store.one3d.payments;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import store.one3d.production.DispatchWorker;
import store.one3d.production.ExecutionObserver;
import store.one3d.support.ApiSteps;
import store.one3d.support.PostgresIntegrationTest;
import store.one3d.support.RazorpayEvents;
import store.one3d.support.SimulatorRig;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Razorpay webhooks with signed fixtures: only a verified, captured payment of
 * the right amount confirms an order, once; everything else is refused, ignored
 * or kept for review, and payment never starts production.
 */
@PostgresIntegrationTest
class RazorpayWebhookIT {

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    DispatchWorker worker;

    @Autowired
    ExecutionObserver observer;

    @Autowired
    SimulatorRig simulator;

    private ApiSteps api;
    private String orderId;
    private String providerOrderId;
    private long amount;

    @BeforeEach
    void openCheckout() throws Exception {
        api = new ApiSteps(mvc, worker, observer, simulator);
        orderId = api.placeOrder();
        String checkout = api.post("/api/orders/" + orderId + "/checkout").andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        providerOrderId = JsonPath.read(checkout, "$.providerOrderId");
        amount = ((Number) JsonPath.read(checkout, "$.amountMinor")).longValue();
    }

    @AfterEach
    void dropFailureTrigger() {
        jdbc.sql("DROP TRIGGER IF EXISTS test_fail_payment ON payment").update();
        jdbc.sql("DROP FUNCTION IF EXISTS test_fail_payment()").update();
    }

    @Test
    void aSignedCapturedPaymentConfirmsTheOrderButStartsNothing() throws Exception {
        long workBefore = count("SELECT count(*) FROM pending_work");

        api.webhook(RazorpayEvents.captured(RazorpayEvents.newPaymentId(), providerOrderId, amount))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome").value("APPLIED"));

        api.get("/api/orders/" + orderId)
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.confirmationSource").value("RAZORPAY_TEST"));
        assertThat(count("SELECT count(*) FROM production_job WHERE order_id = ?::uuid", orderId)).isZero();
        assertThat(count("SELECT count(*) FROM pending_work")).isEqualTo(workBefore);
        assertThat(checkoutStatus()).isEqualTo("PAID");
        api.get("/api/orders/" + orderId + "/timeline")
                .andExpect(jsonPath("$[1].kind").value("CONFIRMED"))
                .andExpect(jsonPath("$[1].detail").value(org.hamcrest.Matchers.startsWith("Paid with Razorpay test mode")));
    }

    /** On the demo Nginx sends its own id; the backend's log lines for the request carry it (Checkpoint 8). */
    @Test
    void theWebhookLogLinesCarryTheRequestIdFromNginx() throws Exception {
        String requestId = "3f2a9c41d7e04b6f8a1c2d3e4f5a6b7c";
        ListAppender<ILoggingEvent> lines = new ListAppender<>();
        lines.start();
        Logger webhookLog = (Logger) LoggerFactory.getLogger(RazorpayWebhooks.class);
        webhookLog.addAppender(lines);
        try {
            String body = RazorpayEvents.captured(RazorpayEvents.newPaymentId(), providerOrderId, amount);
            mvc.perform(post("/api/payments/razorpay/webhook").with(anonymous())
                            .contentType(MediaType.APPLICATION_JSON).content(body)
                            .header("X-Razorpay-Event-Id", RazorpayEvents.newEventId())
                            .header("X-Razorpay-Signature", RazorpayEvents.sign(body))
                            .header("X-Request-Id", requestId))
                    .andExpect(status().isOk())
                    .andExpect(header().string("X-Request-Id", requestId));
        } finally {
            webhookLog.detachAppender(lines);
        }
        assertThat(lines.list).isNotEmpty().allSatisfy(line ->
                assertThat(line.getMDCPropertyMap()).containsEntry("request_id", requestId));
    }

    @Test
    void aForgedOrTamperedWebhookIsRefusedAndNothingIsRecorded() throws Exception {
        String body = RazorpayEvents.captured(RazorpayEvents.newPaymentId(), providerOrderId, amount);
        String eventId = RazorpayEvents.newEventId();

        api.webhook(body, eventId, RazorpayEvents.sign(body, "not-the-webhook-secret"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_SIGNATURE"));
        String tampered = body.replace("\"amount\": " + amount, "\"amount\": " + (amount + 1));
        assertThat(tampered).isNotEqualTo(body);
        api.webhook(tampered, eventId, RazorpayEvents.sign(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_SIGNATURE"));
        api.webhook(body, eventId, null).andExpect(status().isBadRequest());
        api.webhook(body, null, RazorpayEvents.sign(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MISSING_EVENT_ID"));

        assertThat(count("SELECT count(*) FROM payment_event WHERE provider_order_id = ?", providerOrderId)).isZero();
        assertThat(count("SELECT count(*) FROM payment WHERE provider_order_id = ?", providerOrderId)).isZero();
        assertThat(orderStatus()).isEqualTo("AWAITING_PAYMENT");
    }

    @Test
    void aWrongAmountOrCurrencyIsKeptForReviewAndDoesNotConfirm() throws Exception {
        api.webhook(RazorpayEvents.captured(RazorpayEvents.newPaymentId(), providerOrderId, amount - 100))
                .andExpect(jsonPath("$.outcome").value("NEEDS_REVIEW"));
        api.webhook(RazorpayEvents.event("payment.captured", RazorpayEvents.newPaymentId(), providerOrderId, amount,
                        "USD", "captured", Instant.now()))
                .andExpect(jsonPath("$.outcome").value("NEEDS_REVIEW"));

        assertThat(orderStatus()).isEqualTo("AWAITING_PAYMENT");
        assertThat(reviewReasons()).containsExactlyInAnyOrder("AMOUNT_MISMATCH", "AMOUNT_MISMATCH");
        api.get("/api/orders/" + orderId + "/timeline")
                .andExpect(jsonPath("$[?(@.kind == 'PAYMENT_UNDER_REVIEW')]").isNotEmpty());

        // The correct payment still confirms the order afterwards.
        api.webhook(RazorpayEvents.captured(RazorpayEvents.newPaymentId(), providerOrderId, amount))
                .andExpect(jsonPath("$.outcome").value("APPLIED"));
        assertThat(orderStatus()).isEqualTo("CONFIRMED");
    }

    @Test
    void aPaymentForAnUnknownProviderOrderIsKeptForReview() throws Exception {
        String unknown = "order_notours" + System.nanoTime();
        api.webhook(RazorpayEvents.captured(RazorpayEvents.newPaymentId(), unknown, amount))
                .andExpect(jsonPath("$.outcome").value("NEEDS_REVIEW"));

        assertThat(jdbc.sql("SELECT review_reason FROM payment WHERE provider_order_id = ?").param(unknown)
                .query(String.class).single()).isEqualTo("UNKNOWN_PROVIDER_ORDER");
    }

    @Test
    void theSameEventDeliveredTwiceChangesNothingTheSecondTime() throws Exception {
        String body = RazorpayEvents.captured(RazorpayEvents.newPaymentId(), providerOrderId, amount);
        String eventId = RazorpayEvents.newEventId();

        api.webhook(body, eventId, RazorpayEvents.sign(body)).andExpect(jsonPath("$.outcome").value("APPLIED"));
        Object confirmedAt = orderRow().get("confirmed_at");
        api.webhook(body, eventId, RazorpayEvents.sign(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome").value("ALREADY_RECORDED"));

        assertThat(count("SELECT count(*) FROM payment_event WHERE provider_event_id = ?", eventId)).isOne();
        assertThat(count("SELECT count(*) FROM payment WHERE provider_order_id = ?", providerOrderId)).isOne();
        assertThat(orderRow().get("confirmed_at")).isEqualTo(confirmedAt);
    }

    @Test
    void orderPaidForAnAlreadyRecordedPaymentChangesNothing() throws Exception {
        String paymentId = RazorpayEvents.newPaymentId();
        api.webhook(RazorpayEvents.captured(paymentId, providerOrderId, amount))
                .andExpect(jsonPath("$.outcome").value("APPLIED"));

        api.webhook(RazorpayEvents.event("order.paid", paymentId, providerOrderId, amount, "INR", "captured",
                        Instant.now()))
                .andExpect(jsonPath("$.outcome").value("ALREADY_RECORDED"));

        assertThat(count("SELECT count(*) FROM payment WHERE provider_payment_id = ?", paymentId)).isOne();
        assertThat(reviewReasons()).isEmpty();
    }

    @Test
    void orderPaidAloneConfirmsTheOrder() throws Exception {
        api.webhook(RazorpayEvents.event("order.paid", RazorpayEvents.newPaymentId(), providerOrderId, amount, "INR",
                        "captured", Instant.now()))
                .andExpect(jsonPath("$.outcome").value("APPLIED"));

        assertThat(orderStatus()).isEqualTo("CONFIRMED");
    }

    @Test
    void authorizedOrFailedNeverConfirmsAndALateAuthorizedEventDoesNotUndoACapture() throws Exception {
        String paymentId = RazorpayEvents.newPaymentId();
        api.webhook(RazorpayEvents.event("payment.authorized", paymentId, providerOrderId, amount, "INR", "authorized",
                        Instant.now()))
                .andExpect(jsonPath("$.outcome").value("IGNORED"));
        api.webhook(RazorpayEvents.event("payment.failed", RazorpayEvents.newPaymentId(), providerOrderId, amount,
                        "INR", "failed", Instant.now()))
                .andExpect(jsonPath("$.outcome").value("IGNORED"));
        assertThat(orderStatus()).isEqualTo("AWAITING_PAYMENT");

        api.webhook(RazorpayEvents.captured(paymentId, providerOrderId, amount))
                .andExpect(jsonPath("$.outcome").value("APPLIED"));
        // Delivered out of order, after the capture.
        api.webhook(RazorpayEvents.event("payment.authorized", paymentId, providerOrderId, amount, "INR", "authorized",
                        Instant.now()))
                .andExpect(jsonPath("$.outcome").value("IGNORED"));

        assertThat(orderStatus()).isEqualTo("CONFIRMED");
    }

    @Test
    void aSecondDistinctPaymentIsFlaggedAndTheOrderIsConfirmedOnce() throws Exception {
        api.webhook(RazorpayEvents.captured(RazorpayEvents.newPaymentId(), providerOrderId, amount))
                .andExpect(jsonPath("$.outcome").value("APPLIED"));

        api.webhook(RazorpayEvents.captured(RazorpayEvents.newPaymentId(), providerOrderId, amount))
                .andExpect(jsonPath("$.outcome").value("NEEDS_REVIEW"));

        assertThat(reviewReasons()).containsExactly("SECOND_PAYMENT");
        assertThat(count("SELECT count(*) FROM payment WHERE order_id = ?::uuid AND outcome = 'APPLIED'", orderId)).isOne();
        assertThat(count("SELECT count(*) FROM production_job WHERE order_id = ?::uuid", orderId)).isZero();
    }

    @Test
    void aPaymentStartedAfterTheCheckoutClosedIsFlagged() throws Exception {
        jdbc.sql("""
                        UPDATE checkout SET created_at = now() - interval '1 hour', expires_at = now() - interval '1 minute'
                        WHERE provider_order_id = ?""")
                .param(providerOrderId).update();

        api.webhook(RazorpayEvents.captured(RazorpayEvents.newPaymentId(), providerOrderId, amount))
                .andExpect(jsonPath("$.outcome").value("NEEDS_REVIEW"));

        assertThat(reviewReasons()).containsExactly("LATE_PAYMENT");
        assertThat(orderStatus()).isEqualTo("AWAITING_PAYMENT");
    }

    @RepeatedTest(5)
    void twoEventsForOnePaymentArrivingTogetherApplyItOnce() throws Exception {
        String paymentId = RazorpayEvents.newPaymentId();
        String captured = RazorpayEvents.captured(paymentId, providerOrderId, amount);
        String paid = RazorpayEvents.event("order.paid", paymentId, providerOrderId, amount, "INR", "captured",
                Instant.now());

        List<String> outcomes = outcomes(concurrently(
                () -> api.webhook(captured).andReturn(), () -> api.webhook(paid).andReturn()));

        assertThat(outcomes).containsExactlyInAnyOrder("APPLIED", "ALREADY_RECORDED");
        assertThat(count("SELECT count(*) FROM payment WHERE provider_payment_id = ?", paymentId)).isOne();
        assertThat(orderStatus()).isEqualTo("CONFIRMED");
    }

    @RepeatedTest(5)
    void twoDistinctPaymentsArrivingTogetherConfirmTheOrderOnce() throws Exception {
        List<String> outcomes = outcomes(concurrently(
                () -> api.webhook(RazorpayEvents.captured(RazorpayEvents.newPaymentId(), providerOrderId, amount)).andReturn(),
                () -> api.webhook(RazorpayEvents.captured(RazorpayEvents.newPaymentId(), providerOrderId, amount)).andReturn()));

        assertThat(outcomes).containsExactlyInAnyOrder("APPLIED", "NEEDS_REVIEW");
        assertThat(count("SELECT count(*) FROM payment WHERE order_id = ?::uuid AND outcome = 'APPLIED'", orderId)).isOne();
        assertThat(reviewReasons()).containsExactly("SECOND_PAYMENT");
    }

    @Test
    void aFailureWhileRecordingIsNotAcknowledgedAndTheRetrySucceeds() throws Exception {
        String failingPayment = RazorpayEvents.newPaymentId();
        jdbc.sql("""
                CREATE FUNCTION test_fail_payment() RETURNS trigger LANGUAGE plpgsql AS $$
                BEGIN
                    RAISE EXCEPTION 'injected payment storage failure';
                END $$""").update();
        jdbc.sql("""
                CREATE TRIGGER test_fail_payment BEFORE INSERT ON payment
                FOR EACH ROW WHEN (NEW.provider_payment_id = '%s') EXECUTE FUNCTION test_fail_payment()
                """.formatted(failingPayment)).update();
        String body = RazorpayEvents.captured(failingPayment, providerOrderId, amount);
        String eventId = RazorpayEvents.newEventId();

        api.webhook(body, eventId, RazorpayEvents.sign(body)).andExpect(status().is5xxServerError());
        // Nothing was kept, not even the receipt: Razorpay will deliver it again.
        assertThat(count("SELECT count(*) FROM payment_event WHERE provider_event_id = ?", eventId)).isZero();
        assertThat(orderStatus()).isEqualTo("AWAITING_PAYMENT");

        dropFailureTrigger();
        api.webhook(body, eventId, RazorpayEvents.sign(body)).andExpect(jsonPath("$.outcome").value("APPLIED"));
        assertThat(orderStatus()).isEqualTo("CONFIRMED");
    }

    @Test
    void operatorsSeeOpenReviewsAndRecordTheirDecision() throws Exception {
        api.webhook(RazorpayEvents.captured(RazorpayEvents.newPaymentId(), providerOrderId, amount + 1))
                .andExpect(jsonPath("$.outcome").value("NEEDS_REVIEW"));
        String paymentId = jdbc.sql("SELECT id::text FROM payment WHERE order_id = ?::uuid").param(orderId)
                .query(String.class).single();

        api.get("/api/ops/payments").andExpect(jsonPath("$[?(@.id == '" + paymentId + "')].reviewReason")
                .value(org.hamcrest.Matchers.contains("AMOUNT_MISMATCH")));
        api.post("/api/ops/payments/" + paymentId + "/resolve", "{\"note\": \"Refunded in the Razorpay dashboard\"}")
                .andExpect(status().isOk());
        api.get("/api/ops/payments").andExpect(jsonPath("$[?(@.id == '" + paymentId + "')]").isEmpty());
        api.post("/api/ops/payments/" + paymentId + "/resolve", "{\"note\": \"again\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("NOTHING_TO_RESOLVE"));
    }

    // --- helpers -------------------------------------------------------------

    private String orderStatus() {
        return orderRow().get("status").toString();
    }

    private Map<String, Object> orderRow() {
        return jdbc.sql("SELECT status, confirmed_at FROM customer_order WHERE id = ?::uuid").param(orderId)
                .query().singleRow();
    }

    private String checkoutStatus() {
        return jdbc.sql("SELECT status FROM checkout WHERE provider_order_id = ?").param(providerOrderId)
                .query(String.class).single();
    }

    private List<String> reviewReasons() {
        return jdbc.sql("SELECT review_reason FROM payment WHERE order_id = ?::uuid AND outcome = 'NEEDS_REVIEW'")
                .param(orderId).query(String.class).list();
    }

    private long count(String sql, Object... params) {
        return jdbc.sql(sql).params(params).query(Long.class).single();
    }

    private static List<String> outcomes(List<MvcResult> results) throws Exception {
        List<String> outcomes = new ArrayList<>();
        for (MvcResult result : results) {
            assertThat(result.getResponse().getStatus()).isEqualTo(200);
            outcomes.add(JsonPath.read(result.getResponse().getContentAsString(), "$.outcome"));
        }
        return outcomes;
    }

    @SafeVarargs
    private static List<MvcResult> concurrently(Callable<MvcResult>... calls) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(calls.length);
        try {
            CountDownLatch start = new CountDownLatch(1);
            List<Future<MvcResult>> futures = new ArrayList<>();
            for (Callable<MvcResult> call : calls) {
                futures.add(pool.submit(() -> {
                    start.await();
                    return call.call();
                }));
            }
            start.countDown();
            List<MvcResult> results = new ArrayList<>();
            for (Future<MvcResult> future : futures) {
                results.add(future.get());
            }
            return results;
        } finally {
            pool.shutdownNow();
        }
    }
}
