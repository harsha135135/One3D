package store.one3d.orders;

/** How an order became confirmed; stored so demo data is never mistaken for real payment. */
public enum ConfirmationSource {
    /** The demo-only shortcut; no payment took place. */
    DEMO_SIMULATED,
    /** A captured Razorpay payment in test mode (no real money), verified by the backend. */
    RAZORPAY_TEST
}
