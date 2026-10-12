package store.one3d.files;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import store.one3d.background.BackgroundJob;
import store.one3d.background.PendingWorkStore;
import store.one3d.background.WorkKind;

import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.List;

/**
 * Checks finalized uploads from the work queue: reads the pinned version (at
 * most the size limit plus one byte), computes its SHA-256 and checks the STL
 * structure. If storage cannot be read, the work is retried with backoff and
 * the file stays VERIFYING.
 */
@Component
public class ArtifactVerifier implements BackgroundJob {

    private static final Logger log = LoggerFactory.getLogger(ArtifactVerifier.class);

    private final PendingWorkStore work;
    private final ArtifactVerification verification;
    private final ObjectStorage storage;
    private final int batchSize;
    private final Duration lease;
    private final long maxUploadBytes;

    ArtifactVerifier(PendingWorkStore work, ArtifactVerification verification, ObjectStorage storage,
                     @Value("${one3d.worker.batch-size}") int batchSize,
                     @Value("${one3d.worker.lease}") Duration lease,
                     @Value("${one3d.files.max-upload-bytes}") long maxUploadBytes) {
        this.work = work;
        this.verification = verification;
        this.storage = storage;
        this.batchSize = batchSize;
        this.lease = lease;
        this.maxUploadBytes = maxUploadBytes;
    }

    /** Claims due checks and runs them. Returns the number of rows claimed. */
    @Override
    public int runOnce() {
        List<PendingWorkStore.ClaimedWork> claimed = work.claim(WorkKind.VERIFY_ARTIFACT, batchSize, lease);
        claimed.forEach(this::process);
        return claimed.size();
    }

    void process(PendingWorkStore.ClaimedWork claimed) {
        ArtifactVerification.Target target = verification.target(claimed.subjectId());
        verification.apply(claimed, check(target));
    }

    private ArtifactVerification.Outcome check(ArtifactVerification.Target target) {
        if (target.status() != ArtifactStatus.VERIFYING) {
            return ArtifactVerification.Outcome.alreadySettled();
        }
        StlCheck.Result result;
        try (InputStream in = storage.openVersion(target.storageKey(), target.versionId())) {
            result = StlCheck.inspect(in, maxUploadBytes);
        } catch (IOException | RuntimeException e) {
            log.warn("Could not read file {} for its check: {}", target.artifactId(), e.getClass().getSimpleName());
            return ArtifactVerification.Outcome.unavailable("Storage read failed: " + e.getClass().getSimpleName());
        }
        if (result.sizeBytes() != target.expectedSize()) {
            return ArtifactVerification.Outcome.failed("The stored file has " + result.sizeBytes()
                    + " bytes, not the " + target.expectedSize() + " bytes that were finalized.");
        }
        return result.passed()
                ? ArtifactVerification.Outcome.passed(result.sha256(), result.sizeBytes())
                : ArtifactVerification.Outcome.failed(result.problem().orElseThrow());
    }
}
