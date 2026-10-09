package store.one3d.orders;

/**
 * Commercial progress of an order. Checkpoint 1 only creates orders; later
 * checkpoints add states through a migration that also extends the
 * customer_order_status_known constraint.
 */
public enum OrderStatus {
    AWAITING_PAYMENT
}
