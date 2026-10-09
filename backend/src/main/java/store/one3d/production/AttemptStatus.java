package store.one3d.production;

/** One execution of a job: RUNNING → COMPLETED or FAILED. Ended attempts never change status again. */
public enum AttemptStatus {
    RUNNING,
    COMPLETED,
    FAILED
}
