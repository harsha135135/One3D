package store.one3d.payments;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import store.one3d.common.Timestamps;
import store.one3d.orders.OrderFactKind;
import store.one3d.orders.OrderService;
import store.one3d.orders.OrderStatus;
import store.one3d.orders.PayableOrder;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Decides what a captured payment means, in the caller's transaction. Used for
 * signed webhooks and for reconciliation against Razorpay's API alike.
 *
 * <p>Lock order: the order row, then the checkout row. The decision is made on
 * data read after both locks, so two reports racing for one order are decided
 * one after the other. The payment row is inserted last with
 * {@code ON CONFLICT DO NOTHING} on the provider's payment id: only the report
 * that wins that insert changes anything. Payment never releases production.
 */
@Component
class PaymentRecorder {

    private static final Logger log = LoggerFactory.getLogger(PaymentRecorder.class);
    private static final Map<ReviewReason, String> CUSTOMER_TEXT = Map.of(
            ReviewReason.UNKNOWN_PROVIDER_ORDER, "A payment could not be matched and is being checked",
            ReviewReason.AMOUNT_MISMATCH, "A payment did not match the amount due and is being checked",
            ReviewReason.LATE_PAYMENT, "A payment arrived after the checkout closed and is being checked",
            ReviewReason.SECOND_PAYMENT, "An extra payment was received and is being checked");

    private final CheckoutStore checkouts;
    private final PaymentStore payments;
    private final OrderService orders;

    PaymentRecorder(CheckoutStore checkouts, PaymentStore payments, OrderService orders) {
        this.checkouts = checkouts;
        this.payments = payments;
        this.orders = orders;
    }

    /** Records one captured payment. The caller has already checked {@code payment.captured()}. */
    @Transactional(propagation = Propagation.MANDATORY)
    public PaymentOutcome record(PaymentGateway.ProviderPayment payment, PaymentSource source) {
        if (!payment.captured()) {
            throw new IllegalArgumentException("Only captured payments are recorded");
        }
        Instant now = Timestamps.now();
        Optional<UUID> orderId = checkouts.orderIdOf(payment.orderId());
        if (orderId.isEmpty()) {
            return review(payment, null, source, ReviewReason.UNKNOWN_PROVIDER_ORDER, now);
        }
        PayableOrder order = orders.lockForPayment(orderId.get());
        CheckoutStore.Checkout checkout = checkouts.lockByProviderOrder(payment.orderId());

        ReviewReason reason = decide(payment, checkout, order);
        if (reason != null) {
            return review(payment, checkout, source, reason, now);
        }
        if (!payments.insertPayment(payment, checkout, source, PaymentOutcome.APPLIED, null, now)) {
            return PaymentOutcome.ALREADY_RECORDED;
        }
        orders.confirmPaid(order.orderId(), "Paid with Razorpay test mode (payment " + payment.id() + ")");
        checkouts.markPaid(checkout);
        log.info("Payment {} confirmed order {}", payment.id(), order.orderId());
        return PaymentOutcome.APPLIED;
    }

    /** Null when the payment confirms the order; otherwise why it needs an operator. */
    private static ReviewReason decide(PaymentGateway.ProviderPayment payment, CheckoutStore.Checkout checkout,
                                       PayableOrder order) {
        if (payment.amountMinor() != checkout.amountMinor() || !payment.currency().equals(checkout.currency())
                || payment.amountMinor() != order.amountMinor() || !payment.currency().equals(order.currency())) {
            return ReviewReason.AMOUNT_MISMATCH;
        }
        if (payment.createdAt().isAfter(checkout.expiresAt())) {
            return ReviewReason.LATE_PAYMENT;
        }
        if (order.status() != OrderStatus.AWAITING_PAYMENT) {
            return ReviewReason.SECOND_PAYMENT;
        }
        return null;
    }

    private PaymentOutcome review(PaymentGateway.ProviderPayment payment, CheckoutStore.Checkout checkout,
                                  PaymentSource source, ReviewReason reason, Instant now) {
        if (!payments.insertPayment(payment, checkout, source, PaymentOutcome.NEEDS_REVIEW, reason, now)) {
            return PaymentOutcome.ALREADY_RECORDED;
        }
        if (checkout != null) {
            orders.recordFact(checkout.orderId(), OrderFactKind.PAYMENT_UNDER_REVIEW, CUSTOMER_TEXT.get(reason));
        }
        log.warn("Payment {} needs review: {}", payment.id(), reason);
        return PaymentOutcome.NEEDS_REVIEW;
    }
}
