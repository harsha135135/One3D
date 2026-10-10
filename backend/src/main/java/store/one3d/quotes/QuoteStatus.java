package store.one3d.quotes;

/** ISSUED until accepted or withdrawn. Expiry is not a status: an ISSUED quote past expires_at cannot be accepted. */
public enum QuoteStatus {
    ISSUED,
    ACCEPTED,
    /** Replaced by a newer quote, or by a new revision of the request. */
    WITHDRAWN
}
