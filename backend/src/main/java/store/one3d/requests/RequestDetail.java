package store.one3d.requests;

import store.one3d.files.ArtifactResponse;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** A request with every revision, oldest first. Quotes are listed separately by the quotes module. */
public record RequestDetail(
        UUID id,
        UUID customerId,
        RequestStatus status,
        String operatorMessage,
        Instant createdAt,
        Instant updatedAt,
        List<Revision> revisions) {

    /** One revision exactly as submitted, with the checked file it names. */
    public record Revision(
            UUID id,
            int number,
            String material,
            String colour,
            String notes,
            Instant createdAt,
            ArtifactResponse file) {
    }
}
