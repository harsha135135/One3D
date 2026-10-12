package store.one3d.requests;

import java.time.Instant;
import java.util.UUID;

/** A request in a list, described by its latest revision. */
public record RequestSummary(
        UUID id,
        RequestStatus status,
        int latestRevision,
        String filename,
        String material,
        String colour,
        Instant createdAt,
        Instant updatedAt) {
}
