package store.one3d.payments;

/** Why a captured payment needs an operator, the "ambiguous or unexpected results" of the V1 scope. */
public enum ReviewReason {
    /** The provider order is not one of ours. */
    UNKNOWN_PROVIDER_ORDER,
    /** Amount or currency differs from the checkout. */
    AMOUNT_MISMATCH,
    /** Started after the checkout's window had closed. */
    LATE_PAYMENT,
    /** The order was already paid (or past payment) when this distinct payment arrived. */
    SECOND_PAYMENT
}
