package store.one3d.files;

import java.time.Instant;
import java.util.UUID;

/**
 * An uploaded file as the API shows it. {@code versionId} and {@code sha256}
 * identify the exact bytes that were checked; the storage key stays internal.
 */
public record ArtifactResponse(
        UUID id,
        String filename,
        long sizeBytes,
        ArtifactStatus status,
        String versionId,
        String sha256,
        String rejectionReason,
        Instant createdAt,
        Instant uploadExpiresAt,
        Instant verifiedAt) {

    static ArtifactResponse from(Artifact artifact) {
        return new ArtifactResponse(
                artifact.getId(),
                artifact.getOriginalFilename(),
                artifact.getDeclaredSize(),
                artifact.getStatus(),
                artifact.getVersionId(),
                artifact.getSha256(),
                artifact.getRejectionReason(),
                artifact.getCreatedAt(),
                artifact.getUploadExpiresAt(),
                artifact.getVerifiedAt());
    }
}
