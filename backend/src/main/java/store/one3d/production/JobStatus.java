package store.one3d.production;

/**
 * Manufacturing progress of the one job for an order.
 *
 * <pre>
 * RELEASED → PRINTING → AWAITING_QC → ACCEPTED
 *    ↑           |            |
 *    └── attempt failed ──────┴── QC failed (a new attempt is needed)
 * </pre>
 *
 * Nothing moves a job out of RELEASED automatically: an operator must assign
 * a printer, and that assignment is the authorization for each attempt.
 */
public enum JobStatus {
    /** Released by an operator and waiting for an attempt to be assigned. */
    RELEASED,
    /** An attempt is running on a printer. */
    PRINTING,
    /** The latest attempt completed; the part needs whole-part inspection. */
    AWAITING_QC,
    /** A part passed QC. Final for V1. */
    ACCEPTED
}
