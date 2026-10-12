package store.one3d.payments;

import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import store.one3d.common.ConflictException;
import store.one3d.common.NotFoundException;
import store.one3d.common.Timestamps;
import store.one3d.orders.OrderResponse;
import store.one3d.orders.OrderService;
import store.one3d.orders.OrderStatus;
import store.one3d.orders.PayableOrder;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Opening a test-mode checkout, and checking with Razorpay whether it was paid.
 * Razorpay is always called outside database transactions; each database step
 * is one short transaction.
 */
@Service
public class CheckoutService {

    /** A checkout this close to its end is replaced rather than reused. */
    private static final Duration MIN_REMAINING = Duration.ofMinutes(2);
    /** At most this many provider orders are asked about per payment check. */
    private static final int MAX_CHECKED = 5;

    private final RazorpayProperties properties;
    private final PaymentGateway gateway;
    private final OrderService orders;
    private final CheckoutStore checkouts;
    private final PaymentRecorder recorder;
    private final TransactionTemplate transaction;

    CheckoutService(RazorpayProperties properties, PaymentGateway gateway, OrderService orders,
                    CheckoutStore checkouts, PaymentRecorder recorder, PlatformTransactionManager transactionManager) {
        this.properties = properties;
        this.gateway = gateway;
        this.orders = orders;
        this.checkouts = checkouts;
        this.recorder = recorder;
        this.transaction = new TransactionTemplate(transactionManager);
    }

    /**
     * Returns the order's open checkout, or opens a new one: a checkout row
     * first (order locked), then a Razorpay order for exactly the order total,
     * then the Razorpay id on the row (even if a parallel call has superseded
     * it meanwhile, so a payment made against it is still matched). A failed
     * Razorpay call leaves a row without a Razorpay id, which the next call
     * replaces.
     */
    public CheckoutResponse open(UUID customerId, UUID orderId) {
        requireEnabled();
        record Prepared(CheckoutStore.Checkout checkout, PayableOrder order, boolean reused) {
        }
        Prepared prepared = transaction.execute(status -> {
            PayableOrder order = orders.requirePayable(customerId, orderId);
            Instant now = Timestamps.now();
            var open = checkouts.openFor(orderId);
            if (open.isPresent() && open.get().providerOrderId() != null
                    && open.get().expiresAt().isAfter(now.plus(MIN_REMAINING))) {
                return new Prepared(open.get(), order, true);
            }
            open.ifPresent(c -> checkouts.supersede(c.id()));
            Instant expiresAt = earlier(now.plus(properties.checkoutWindow()), order.paymentWindowEndsAt());
            if (!expiresAt.isAfter(now.plus(MIN_REMAINING))) {
                throw new ConflictException("PAYMENT_WINDOW_CLOSED", "The payment window for order " + orderId
                        + " closes at " + order.paymentWindowEndsAt() + "; too soon to start a checkout.");
            }
            CheckoutStore.Checkout checkout = new CheckoutStore.Checkout(UUID.randomUUID(), orderId, null,
                    order.amountMinor(), order.currency(), "OPEN", now, expiresAt);
            checkouts.insert(checkout);
            return new Prepared(checkout, order, false);
        });
        if (prepared.reused()) {
            return response(prepared.checkout(), prepared.checkout().providerOrderId(), prepared.order());
        }

        CheckoutStore.Checkout checkout = prepared.checkout();
        PaymentGateway.ProviderOrder providerOrder;
        try {
            providerOrder = gateway.createOrder(checkout.id().toString(), checkout.amountMinor(), checkout.currency());
        } catch (PaymentProviderException e) {
            throw new ProviderUnavailableException();
        }
        if (providerOrder.amountMinor() != checkout.amountMinor() || !providerOrder.currency().equals(checkout.currency())) {
            throw new IllegalStateException("Razorpay created an order for a different amount");
        }
        transaction.executeWithoutResult(status -> checkouts.attachProviderOrder(checkout.id(), providerOrder.id()));
        return response(checkout, providerOrder.id(), prepared.order());
    }

    /**
     * Asks Razorpay which payments were made for the customer's order and
     * records the captured ones, exactly as a webhook would. What the browser
     * reported is not used. Does nothing unless the order still awaits payment.
     */
    public PaymentCheckResponse check(UUID customerId, UUID orderId) {
        requireEnabled();
        record Candidates(OrderStatus status, List<String> providerOrders) {
        }
        Candidates candidates = transaction.execute(status -> {
            OrderResponse order = orders.getOwnOrder(customerId, orderId);
            return new Candidates(order.status(), order.status() == OrderStatus.AWAITING_PAYMENT
                    ? checkouts.providerOrdersOf(orderId, MAX_CHECKED) : List.of());
        });
        List<PaymentGateway.ProviderPayment> captured = new ArrayList<>();
        for (String providerOrderId : candidates.providerOrders()) {
            try {
                gateway.paymentsOf(providerOrderId).stream().filter(PaymentGateway.ProviderPayment::captured)
                        .forEach(captured::add);
            } catch (PaymentProviderException e) {
                throw new ProviderUnavailableException();
            }
        }
        int recorded = 0;
        for (PaymentGateway.ProviderPayment payment : captured) {
            PaymentOutcome outcome = transaction.execute(status -> recorder.record(payment, PaymentSource.RECONCILIATION));
            if (outcome == PaymentOutcome.APPLIED || outcome == PaymentOutcome.NEEDS_REVIEW) {
                recorded++;
            }
        }
        OrderStatus now = transaction.execute(status -> orders.getOwnOrder(customerId, orderId).status());
        return new PaymentCheckResponse(now, recorded);
    }

    private CheckoutResponse response(CheckoutStore.Checkout checkout, String providerOrderId, PayableOrder order) {
        return new CheckoutResponse(checkout.id(), checkout.orderId(), properties.keyId(), providerOrderId,
                checkout.amountMinor(), checkout.currency(), checkout.expiresAt(), order.itemName(), true);
    }

    private void requireEnabled() {
        if (!properties.enabled()) {
            throw new NotFoundException("PAYMENTS_DISABLED", "Payments are not enabled in this environment.");
        }
    }

    private static Instant earlier(Instant a, Instant b) {
        return a.isBefore(b) ? a : b;
    }
}
