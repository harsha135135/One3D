package store.one3d.background;

/** What a {@code pending_work} row asks a worker to do. */
public enum WorkKind {
    /** Deliver a start command to the printer simulator ({@code command_id}). */
    DISPATCH_START,
    /** Read an uploaded file's pinned version and check it ({@code artifact_id}). */
    VERIFY_ARTIFACT
}
