package store.one3d.orders;

import java.time.Instant;
import java.util.UUID;

/** What the payments module needs from an order: who owns it, its state, the amount due and the payment window. */
public record PayableOrder(
        UUID orderId,
        UUID customerId,
        OrderStatus status,
        long amountMinor,
        String currency,
        Instant paymentWindowEndsAt,
        String itemName) {

    static PayableOrder from(CustomerOrder order) {
        return new PayableOrder(order.getId(), order.getCustomerId(), order.getStatus(), order.total().amountMinor(),
                order.total().currency(), order.getPaymentWindowEndsAt(), order.getLine().getProductName());
    }
}
