package store.one3d.orders;

/** How an order became confirmed; stored so demo data is never mistaken for real payment. */
public enum ConfirmationSource {
    /** The demo-only shortcut; no payment took place. Checkpoint 6 adds test-mode payments. */
    DEMO_SIMULATED
}
