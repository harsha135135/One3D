package store.one3d.payments;

/** How the backend learned about a payment; both are authenticated server-side, never the browser. */
enum PaymentSource {
    /** A Razorpay webhook with a valid signature. */
    WEBHOOK,
    /** The backend asked Razorpay's API for the order's payments. */
    RECONCILIATION
}
