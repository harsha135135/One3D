package store.one3d.support;

import store.one3d.payments.PaymentGateway;
import store.one3d.payments.PaymentProviderException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * An in-memory payment provider for integration tests: creates provider orders
 * and reports whatever payments a test adds. Real Razorpay is used only in the
 * local sandbox run (see the Checkpoint 6 evidence), never in CI.
 */
public final class FakeRazorpay implements PaymentGateway {

    private final AtomicInteger sequence = new AtomicInteger();
    private final Map<String, ProviderOrder> orders = new ConcurrentHashMap<>();
    private final Map<String, List<ProviderPayment>> payments = new ConcurrentHashMap<>();
    private final AtomicInteger failNextCreates = new AtomicInteger();
    private final AtomicInteger createCalls = new AtomicInteger();
    private final AtomicInteger paymentQueries = new AtomicInteger();

    @Override
    public ProviderOrder createOrder(String receipt, long amountMinor, String currency) throws PaymentProviderException {
        createCalls.incrementAndGet();
        if (failNextCreates.getAndUpdate(n -> Math.max(0, n - 1)) > 0) {
            throw new PaymentProviderException("simulated provider outage");
        }
        ProviderOrder order = new ProviderOrder("order_fake" + sequence.incrementAndGet() + System.nanoTime(),
                amountMinor, currency);
        orders.put(order.id(), order);
        return order;
    }

    @Override
    public List<ProviderPayment> paymentsOf(String providerOrderId) {
        paymentQueries.incrementAndGet();
        return List.copyOf(payments.getOrDefault(providerOrderId, List.of()));
    }

    /** The next {@code count} order creations fail as if Razorpay were down. */
    public void failNextOrderCreations(int count) {
        failNextCreates.set(count);
    }

    /** Adds a payment the provider will report for the order (as if the customer paid in Checkout). */
    public ProviderPayment addPayment(String providerOrderId, long amountMinor, String status) {
        ProviderPayment payment = new ProviderPayment("pay_fake" + sequence.incrementAndGet() + System.nanoTime(),
                providerOrderId, amountMinor, "INR", status, Instant.now());
        payments.computeIfAbsent(providerOrderId, id -> new ArrayList<>()).add(payment);
        return payment;
    }

    public int createCalls() {
        return createCalls.get();
    }

    public int paymentQueries() {
        return paymentQueries.get();
    }
}
