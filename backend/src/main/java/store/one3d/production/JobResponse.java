package store.one3d.production;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** API representation of a production job with all of its attempts, oldest first. */
public record JobResponse(
        UUID id,
        UUID orderId,
        UUID recipeRevisionId,
        JobStatus status,
        Instant releasedAt,
        Instant acceptedAt,
        List<Attempt> attempts) {

    public record Attempt(
            UUID id,
            int attemptNumber,
            UUID printerId,
            AttemptStatus status,
            Instant startedAt,
            Instant endedAt,
            String failureReason,
            QcResult qcResult,
            String qcNotes,
            Instant qcRecordedAt) {

        static Attempt from(PrintAttempt attempt) {
            return new Attempt(
                    attempt.getId(),
                    attempt.getAttemptNumber(),
                    attempt.getPrinterId(),
                    attempt.getStatus(),
                    attempt.getStartedAt(),
                    attempt.getEndedAt(),
                    attempt.getFailureReason(),
                    attempt.getQcResult(),
                    attempt.getQcNotes(),
                    attempt.getQcRecordedAt());
        }
    }

    static JobResponse from(ProductionJob job) {
        return new JobResponse(
                job.getId(),
                job.getOrderId(),
                job.getRecipeRevisionId(),
                job.getStatus(),
                job.getReleasedAt(),
                job.getAcceptedAt(),
                job.getAttempts().stream().map(Attempt::from).toList());
    }
}
