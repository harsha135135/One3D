package store.one3d.orders;

/**
 * Commercial progress of an order. Manufacturing progress is tracked
 * separately on the production job and its attempts.
 *
 * <pre>
 * AWAITING_PAYMENT → CONFIRMED → READY_FOR_HANDOFF → HANDED_OVER
 * </pre>
 *
 * Adding a state needs a migration that extends the
 * customer_order_status_known constraint.
 */
public enum OrderStatus {
    AWAITING_PAYMENT,
    /** Commercially eligible for production. Does not start production by itself. */
    CONFIRMED,
    /** The accepted part is ready to be handed to the customer. */
    READY_FOR_HANDOFF,
    /** An operator recorded that the customer received the part. */
    HANDED_OVER
}
