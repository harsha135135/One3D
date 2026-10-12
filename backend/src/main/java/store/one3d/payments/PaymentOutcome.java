package store.one3d.payments;

/** What happened to a reported payment or webhook event. */
public enum PaymentOutcome {
    /** The captured payment confirmed its order. */
    APPLIED,
    /** This payment (or event) was already recorded; nothing changed. */
    ALREADY_RECORDED,
    /** Inconsistent or unexpected; kept for an operator, and the order did not change. */
    NEEDS_REVIEW,
    /** Not a captured payment (for example payment.authorized or payment.failed); nothing changed. */
    IGNORED
}
