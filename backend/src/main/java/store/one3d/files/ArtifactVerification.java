package store.one3d.files;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import store.one3d.background.PendingWorkStore;
import store.one3d.common.Timestamps;

import java.time.Duration;
import java.util.UUID;

/**
 * The database side of checking a file, one short transaction per step. The
 * file itself is read between these steps, outside any transaction.
 */
@Service
class ArtifactVerification {

    private static final Logger log = LoggerFactory.getLogger(ArtifactVerification.class);

    /** What to read: one exact stored version. */
    record Target(UUID artifactId, ArtifactStatus status, String storageKey, String versionId, long expectedSize) {
    }

    /** What the check found. {@code UNAVAILABLE} means storage could not be read; the work is retried. */
    record Outcome(Kind kind, String sha256, long sizeBytes, String reason) {

        enum Kind { PASSED, FAILED, UNAVAILABLE, ALREADY_SETTLED }

        static Outcome passed(String sha256, long sizeBytes) {
            return new Outcome(Kind.PASSED, sha256, sizeBytes, null);
        }

        static Outcome failed(String reason) {
            return new Outcome(Kind.FAILED, null, 0, reason);
        }

        static Outcome unavailable(String reason) {
            return new Outcome(Kind.UNAVAILABLE, null, 0, reason);
        }

        static Outcome alreadySettled() {
            return new Outcome(Kind.ALREADY_SETTLED, null, 0, null);
        }
    }

    private final ArtifactRepository artifacts;
    private final PendingWorkStore work;
    private final Duration retryBackoff;

    ArtifactVerification(ArtifactRepository artifacts, PendingWorkStore work,
                         @Value("${one3d.worker.retry-backoff}") Duration retryBackoff) {
        this.artifacts = artifacts;
        this.work = work;
        this.retryBackoff = retryBackoff;
    }

    @Transactional(readOnly = true)
    public Target target(UUID artifactId) {
        Artifact artifact = artifacts.findById(artifactId).orElseThrow();
        return new Target(artifact.getId(), artifact.getStatus(), artifact.getStorageKey(), artifact.getVersionId(),
                artifact.getDeclaredSize());
    }

    /**
     * Records a check result. The claim-token check runs first: if another
     * worker has taken over this row, nothing at all is changed.
     */
    @Transactional
    public boolean apply(PendingWorkStore.ClaimedWork claimed, Outcome outcome) {
        boolean stillOurs = outcome.kind() == Outcome.Kind.UNAVAILABLE
                ? work.retryLaterIfStillClaimed(claimed, outcome.reason(), backoffFor(claimed.attempts()))
                : work.completeIfStillClaimed(claimed);
        if (!stillOurs) {
            log.warn("Discarding late check result {} for work {}: the claim was taken over", outcome.kind(),
                    claimed.id());
            return false;
        }
        if (outcome.kind() == Outcome.Kind.UNAVAILABLE || outcome.kind() == Outcome.Kind.ALREADY_SETTLED) {
            return true;
        }
        Artifact artifact = artifacts.findForUpdate(claimed.subjectId()).orElseThrow();
        if (artifact.getStatus() != ArtifactStatus.VERIFYING) {
            return true;
        }
        if (outcome.kind() == Outcome.Kind.PASSED) {
            artifact.markVerified(outcome.sha256(), outcome.sizeBytes(), Timestamps.now());
        } else {
            artifact.markRejected(outcome.reason(), Timestamps.now());
        }
        return true;
    }

    private Duration backoffFor(int attemptsSoFar) {
        // 1x, 2x, 4x ... the base backoff, capped at 32x.
        return retryBackoff.multipliedBy(1L << Math.min(attemptsSoFar - 1, 5));
    }
}
