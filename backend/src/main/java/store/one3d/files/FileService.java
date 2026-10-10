package store.one3d.files;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import store.one3d.background.PendingWorkStore;
import store.one3d.background.WorkKind;
import store.one3d.common.ConflictException;
import store.one3d.common.Timestamps;

import java.net.URI;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Upload, finalize and download of private model files, plus the small API
 * other modules use. Storage is never called inside a database transaction.
 */
@Service
public class FileService {

    private final ArtifactRepository artifacts;
    private final ObjectStorage storage;
    private final PendingWorkStore work;
    private final StorageProperties storageProperties;
    private final TransactionTemplate transaction;
    private final long maxUploadBytes;
    private final int verifyMaxAttempts;

    FileService(ArtifactRepository artifacts, ObjectStorage storage, PendingWorkStore work,
                StorageProperties storageProperties, PlatformTransactionManager transactionManager,
                @Value("${one3d.files.max-upload-bytes}") long maxUploadBytes,
                @Value("${one3d.files.verify-max-attempts}") int verifyMaxAttempts) {
        this.artifacts = artifacts;
        this.storage = storage;
        this.work = work;
        this.storageProperties = storageProperties;
        this.transaction = new TransactionTemplate(transactionManager);
        this.maxUploadBytes = maxUploadBytes;
        this.verifyMaxAttempts = verifyMaxAttempts;
    }

    /**
     * Records a new upload under a random key and signs a PUT for exactly the
     * declared size. Nothing is trusted yet: finalize and verification check
     * what actually arrived.
     */
    @Transactional
    public UploadResponse startUpload(UUID ownerId, String requestedName, long sizeBytes) {
        String filename = FileNames.sanitize(requestedName).orElseThrow(() -> new InvalidUploadException(
                "INVALID_FILE_NAME", "Upload one .stl file (name up to " + FileNames.MAX_LENGTH + " characters)."));
        if (sizeBytes < 1 || sizeBytes > maxUploadBytes) {
            throw new InvalidUploadException("FILE_TOO_LARGE",
                    "A model file must be between 1 and " + maxUploadBytes + " bytes.");
        }
        Artifact artifact = artifacts.save(Artifact.pendingUpload(
                ownerId, filename, sizeBytes, Timestamps.now(), storageProperties.uploadTtl()));
        ObjectStorage.PresignedUpload upload =
                storage.presignUpload(artifact.getStorageKey(), sizeBytes, storageProperties.uploadTtl());
        return new UploadResponse(ArtifactResponse.from(artifact), new UploadResponse.Upload(
                "PUT", upload.url(), upload.headers(), upload.expiresAt(), maxUploadBytes));
    }

    /**
     * Confirms the upload: the stored object must exist and have the declared
     * size. Its exact version is pinned and the content check is queued in the
     * same transaction. Repeating it returns the current state.
     */
    public ArtifactResponse finalizeUpload(UUID ownerId, UUID artifactId) {
        Artifact pending = transaction.execute(status -> {
            Artifact artifact = findOwned(ownerId, artifactId);
            if (artifact.getStatus() == ArtifactStatus.UPLOAD_PENDING) {
                artifact.requireUploadOpen(Timestamps.now());
            }
            return artifact;
        });
        if (pending.getStatus() != ArtifactStatus.UPLOAD_PENDING) {
            return ArtifactResponse.from(pending);
        }

        ObjectStorage.StoredObject stored = headOutsideTransaction(pending.getStorageKey())
                .orElseThrow(() -> new ConflictException("UPLOAD_NOT_FOUND",
                        "Nothing has been uploaded for file " + artifactId + " yet."));
        if (stored.sizeBytes() != pending.getDeclaredSize()) {
            throw new ConflictException("UPLOAD_SIZE_MISMATCH", "The stored file has " + stored.sizeBytes()
                    + " bytes, not the declared " + pending.getDeclaredSize() + ".");
        }
        String versionId = stored.versionId().orElseThrow(() -> new StorageProblemException("STORAGE_NOT_VERSIONED",
                "File storage is not versioned, so the upload cannot be pinned. Nothing was changed."));

        return transaction.execute(status -> {
            Artifact artifact = artifacts.findForUpdate(artifactId).orElseThrow();
            if (artifact.getStatus() == ArtifactStatus.UPLOAD_PENDING) {
                artifact.finalizeUpload(versionId, Timestamps.now());
                work.enqueue(WorkKind.VERIFY_ARTIFACT, artifactId, verifyMaxAttempts);
            }
            return ArtifactResponse.from(artifact);
        });
    }

    @Transactional(readOnly = true)
    public ArtifactResponse getOwn(UUID ownerId, UUID artifactId) {
        return ArtifactResponse.from(findOwned(ownerId, artifactId));
    }

    /** A short-lived download link for the customer's own checked file. */
    public URI ownDownload(UUID ownerId, UUID artifactId) {
        Artifact artifact = transaction.execute(status -> findOwned(ownerId, artifactId));
        return downloadOf(artifact);
    }

    /**
     * A short-lived download link for a checked file, for operators. The caller
     * must already have decided that this person may see the file.
     */
    public URI downloadForReview(UUID artifactId) {
        Artifact artifact = transaction.execute(status -> artifacts.findById(artifactId)
                .orElseThrow(() -> new ArtifactNotFoundException(artifactId)));
        return downloadOf(artifact);
    }

    /**
     * For a submission: the file must belong to this person and have passed
     * its check. Joins the caller's transaction.
     */
    @Transactional(readOnly = true)
    public ArtifactResponse requireSubmittable(UUID ownerId, UUID artifactId) {
        Artifact artifact = findOwned(ownerId, artifactId);
        if (artifact.getStatus() != ArtifactStatus.VERIFIED) {
            throw new ConflictException("FILE_NOT_VERIFIED",
                    "File " + artifactId + " is " + artifact.getStatus() + "; only checked files can be submitted.");
        }
        return ArtifactResponse.from(artifact);
    }

    /** Files by id, for building views in other modules. */
    @Transactional(readOnly = true)
    public Map<UUID, ArtifactResponse> describe(Collection<UUID> artifactIds) {
        return artifacts.findAllById(artifactIds).stream()
                .map(ArtifactResponse::from)
                .collect(Collectors.toMap(ArtifactResponse::id, Function.identity()));
    }

    /** An operator asks for a check whose tries ran out to be tried again. */
    @Transactional
    public ArtifactResponse retryVerification(UUID artifactId) {
        Artifact artifact = artifacts.findById(artifactId).orElseThrow(() -> new ArtifactNotFoundException(artifactId));
        if (work.requeueExhausted(WorkKind.VERIFY_ARTIFACT, artifactId) == 0) {
            throw new ConflictException("NOTHING_TO_RETRY", "File " + artifactId + " has no exhausted check to retry.");
        }
        return ArtifactResponse.from(artifact);
    }

    private URI downloadOf(Artifact artifact) {
        if (artifact.getStatus() != ArtifactStatus.VERIFIED) {
            throw new ConflictException("FILE_NOT_VERIFIED",
                    "File " + artifact.getId() + " is " + artifact.getStatus() + "; only checked files can be downloaded.");
        }
        try {
            return storage.presignDownload(artifact.getStorageKey(), artifact.getVersionId(),
                    artifact.getOriginalFilename(), storageProperties.downloadTtl());
        } catch (RuntimeException e) {
            throw new StorageProblemException("STORAGE_UNAVAILABLE", "The download link could not be created.");
        }
    }

    private Optional<ObjectStorage.StoredObject> headOutsideTransaction(String key) {
        try {
            return storage.head(key);
        } catch (StorageUnavailableException e) {
            throw new StorageProblemException("STORAGE_UNAVAILABLE",
                    "File storage could not be reached. Nothing was changed; try again.");
        }
    }

    private Artifact findOwned(UUID ownerId, UUID artifactId) {
        return artifacts.findByIdAndOwnerId(artifactId, ownerId)
                .orElseThrow(() -> new ArtifactNotFoundException(artifactId));
    }
}
