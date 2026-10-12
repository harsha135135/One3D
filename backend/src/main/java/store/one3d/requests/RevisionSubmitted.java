package store.one3d.requests;

import java.util.UUID;

/**
 * Published inside the transaction that stores a new revision of an existing
 * request. Listeners run synchronously in that same transaction: the quotes
 * module withdraws the open quote, so an approval never carries over to
 * different content.
 */
public record RevisionSubmitted(UUID requestId, UUID revisionId, int revisionNumber) {
}
