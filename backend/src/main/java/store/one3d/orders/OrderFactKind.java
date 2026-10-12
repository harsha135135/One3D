package store.one3d.orders;

/**
 * Milestones on the customer's timeline. Each is recorded once per order by
 * the module that caused it, except PAYMENT_UNDER_REVIEW, which can repeat.
 */
public enum OrderFactKind {
    PLACED,
    /** Confirmed for production; the detail says how (test-mode payment or demo shortcut). */
    CONFIRMED,
    /** A payment needs a manual check; the order did not change. */
    PAYMENT_UNDER_REVIEW,
    RELEASED_TO_PRODUCTION,
    QC_PASSED,
    READY_FOR_HANDOFF,
    HANDED_OVER
}
