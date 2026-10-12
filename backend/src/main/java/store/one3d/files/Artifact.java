package store.one3d.files;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import store.one3d.common.ConflictException;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * An uploaded model. Its storage key is random and never chosen by the client.
 * Finalizing pins the exact stored version; checks, approval and orders refer
 * to that version and its SHA-256, never to "whatever is at this key now".
 */
@Entity
@Table(name = "artifact")
class Artifact {

    @Id
    private UUID id;

    @Column(name = "owner_id", updatable = false)
    private UUID ownerId;

    @Column(name = "storage_key", updatable = false)
    private String storageKey;

    @Column(name = "original_filename", updatable = false)
    private String originalFilename;

    @Column(name = "declared_size", updatable = false)
    private long declaredSize;

    @Enumerated(EnumType.STRING)
    private ArtifactStatus status;

    @Column(name = "version_id")
    private String versionId;

    private String sha256;

    @Column(name = "verified_size")
    private Long verifiedSize;

    @Column(name = "rejection_reason")
    private String rejectionReason;

    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @Column(name = "upload_expires_at", updatable = false)
    private Instant uploadExpiresAt;

    @Column(name = "finalized_at")
    private Instant finalizedAt;

    @Column(name = "verified_at")
    private Instant verifiedAt;

    protected Artifact() {
        // for JPA
    }

    static Artifact pendingUpload(UUID ownerId, String filename, long declaredSize, Instant now, Duration uploadTtl) {
        Artifact artifact = new Artifact();
        artifact.id = UUID.randomUUID();
        artifact.ownerId = ownerId;
        artifact.storageKey = "uploads/" + artifact.id;
        artifact.originalFilename = filename;
        artifact.declaredSize = declaredSize;
        artifact.status = ArtifactStatus.UPLOAD_PENDING;
        artifact.createdAt = now;
        artifact.uploadExpiresAt = now.plus(uploadTtl);
        return artifact;
    }

    void requireUploadOpen(Instant now) {
        if (status != ArtifactStatus.UPLOAD_PENDING) {
            throw new ConflictException("UPLOAD_ALREADY_FINALIZED", "File " + id + " is " + status + ".");
        }
        if (now.isAfter(uploadExpiresAt)) {
            throw new ConflictException("UPLOAD_EXPIRED", "The upload window for file " + id + " has closed.");
        }
    }

    /** Pins the exact stored version; the content check runs next. */
    void finalizeUpload(String pinnedVersionId, Instant now) {
        requireUploadOpen(now);
        versionId = pinnedVersionId;
        status = ArtifactStatus.VERIFYING;
        finalizedAt = now;
    }

    void markVerified(String sha256Hex, long sizeBytes, Instant now) {
        requireVerifying();
        sha256 = sha256Hex;
        verifiedSize = sizeBytes;
        status = ArtifactStatus.VERIFIED;
        verifiedAt = now;
    }

    void markRejected(String reason, Instant now) {
        requireVerifying();
        rejectionReason = reason.length() > 500 ? reason.substring(0, 500) : reason;
        status = ArtifactStatus.REJECTED;
        verifiedAt = now;
    }

    private void requireVerifying() {
        if (status != ArtifactStatus.VERIFYING) {
            throw new IllegalStateException("File " + id + " is " + status + ", not VERIFYING");
        }
    }

    UUID getId() {
        return id;
    }

    UUID getOwnerId() {
        return ownerId;
    }

    String getStorageKey() {
        return storageKey;
    }

    String getOriginalFilename() {
        return originalFilename;
    }

    long getDeclaredSize() {
        return declaredSize;
    }

    ArtifactStatus getStatus() {
        return status;
    }

    String getVersionId() {
        return versionId;
    }

    String getSha256() {
        return sha256;
    }

    Long getVerifiedSize() {
        return verifiedSize;
    }

    String getRejectionReason() {
        return rejectionReason;
    }

    Instant getCreatedAt() {
        return createdAt;
    }

    Instant getUploadExpiresAt() {
        return uploadExpiresAt;
    }

    Instant getVerifiedAt() {
        return verifiedAt;
    }
}
