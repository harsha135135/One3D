package store.one3d.requests;

/** Where a custom request is. Every new submission revision returns it to SUBMITTED for review. */
public enum RequestStatus {
    /** Waiting for an operator to review the latest revision. */
    SUBMITTED,
    /** The operator asked the customer for a new revision. */
    CHANGES_REQUIRED,
    /** Closed by the operator; nothing more can happen. */
    REJECTED,
    /** A quote for the latest revision is open for acceptance. */
    QUOTED,
    /** The customer accepted a quote; the order carries on from here. */
    ORDERED
}
