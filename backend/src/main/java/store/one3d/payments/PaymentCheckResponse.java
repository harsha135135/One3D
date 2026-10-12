package store.one3d.payments;

import store.one3d.orders.OrderStatus;

/** The result of asking Razorpay about an order's payments: how many captured payments were recorded now, and the order's state. */
public record PaymentCheckResponse(OrderStatus orderStatus, int newlyRecorded) {
}
