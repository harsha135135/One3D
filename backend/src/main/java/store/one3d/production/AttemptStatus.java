package store.one3d.production;

/**
 * One execution of a job on a printer.
 *
 * <pre>
 * START_INTENT ──acknowledged──▶ RUNNING ──▶ COMPLETED | FAILED
 *      │  └──reply lost / timeout──▶ RECONCILIATION_REQUIRED ──journal says started──▶ RUNNING
 *      └──simulator refused──▶ REJECTED_BEFORE_START
 * </pre>
 *
 * START_INTENT, RUNNING and RECONCILIATION_REQUIRED are "active": the attempt
 * owns its printer and job. An uncertain start keeps that ownership; it never
 * becomes a new attempt by itself.
 */
public enum AttemptStatus {
    START_INTENT,
    RUNNING,
    COMPLETED,
    FAILED,
    /** The simulator refused the command; nothing was printed. Does not count toward the attempt limit. */
    REJECTED_BEFORE_START,
    RECONCILIATION_REQUIRED;

    boolean isActive() {
        return this == START_INTENT || this == RUNNING || this == RECONCILIATION_REQUIRED;
    }
}
