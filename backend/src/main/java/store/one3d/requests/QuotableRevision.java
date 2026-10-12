package store.one3d.requests;

import java.util.UUID;

/**
 * The exact revision a quote prices, for the quotes module: the request and
 * its owner, the requirements, and the checked file's pinned version and digest.
 */
public record QuotableRevision(
        UUID requestId,
        UUID customerId,
        UUID revisionId,
        int revisionNumber,
        String material,
        String colour,
        UUID artifactId,
        String filename,
        String artifactVersionId,
        String artifactSha256) {
}
