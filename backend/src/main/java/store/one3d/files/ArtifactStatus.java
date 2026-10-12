package store.one3d.files;

/** Where an uploaded file is in its checks. Only VERIFIED files can be submitted or downloaded. */
public enum ArtifactStatus {
    /** A presigned upload was issued; nothing is pinned yet. */
    UPLOAD_PENDING,
    /** The upload was finalized and its exact version pinned; the check is queued. */
    VERIFYING,
    VERIFIED,
    REJECTED
}
