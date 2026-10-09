package store.one3d.background;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

/**
 * The durable work queue (table {@code pending_work}), in plain SQL.
 *
 * <p>A worker claims rows of one {@link WorkKind} with {@code FOR UPDATE SKIP
 * LOCKED}, so two workers never claim the same row, and receives a fresh
 * {@code claim_token} plus a lease. If the worker dies, the lease expires and
 * another worker reclaims the row with a new token. Every later update requires
 * the token it was given: a worker that comes back late finds the token
 * changed, updates zero rows and must discard its result.
 */
@Component
public class PendingWorkStore {

    /**
     * A row this worker holds until {@code claimToken} is replaced.
     * {@code subjectId} is the command or artifact the work is about.
     */
    public record ClaimedWork(UUID id, WorkKind kind, UUID subjectId, UUID claimToken, int attempts,
                              int maxAttempts) {
    }

    /** A row as shown to operators. Exactly one of commandId and artifactId is set, matching the kind. */
    public record WorkView(UUID id, String kind, UUID commandId, UUID artifactId, String status, int attempts,
                           int maxAttempts, String lastError) {
    }

    private final JdbcClient jdbc;

    PendingWorkStore(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** Joins the caller's transaction, so the work exists exactly when the business change does. */
    public void enqueue(WorkKind kind, UUID subjectId, int maxAttempts) {
        jdbc.sql("""
                        INSERT INTO pending_work (id, kind, %s, status, attempts, max_attempts,
                                                  available_at, created_at, updated_at)
                        VALUES (?, ?, ?, 'PENDING', 0, ?, now(), now(), now())
                        """.formatted(subjectColumn(kind)))
                .params(UUID.randomUUID(), kind.name(), subjectId, maxAttempts)
                .update();
    }

    /**
     * Claims up to {@code limit} rows of this kind that are due, or whose
     * previous claim expired. Expired claims that already used all their tries
     * become EXHAUSTED.
     */
    @Transactional
    public List<ClaimedWork> claim(WorkKind kind, int limit, Duration lease) {
        jdbc.sql("""
                        UPDATE pending_work
                        SET status = 'EXHAUSTED', claim_token = NULL, claimed_until = NULL, updated_at = now(),
                            last_error = coalesce(last_error, 'Lease expired after the last allowed try')
                        WHERE kind = ? AND status = 'CLAIMED' AND claimed_until < now() AND attempts >= max_attempts
                        """)
                .param(kind.name())
                .update();
        List<UUID> due = jdbc.sql("""
                        SELECT id FROM pending_work
                        WHERE kind = ?
                          AND ((status = 'PENDING' AND available_at <= now())
                               OR (status = 'CLAIMED' AND claimed_until < now()))
                        ORDER BY created_at
                        LIMIT ?
                        FOR UPDATE SKIP LOCKED
                        """)
                .params(kind.name(), limit)
                .query(UUID.class)
                .list();
        return due.stream().map(id -> claimRow(kind, id, lease)).toList();
    }

    private ClaimedWork claimRow(WorkKind kind, UUID id, Duration lease) {
        UUID token = UUID.randomUUID();
        return jdbc.sql("""
                        UPDATE pending_work
                        SET status = 'CLAIMED', claim_token = ?, attempts = attempts + 1,
                            claimed_until = now() + make_interval(secs => ?), updated_at = now()
                        WHERE id = ?
                        RETURNING id, %s AS subject_id, claim_token, attempts, max_attempts
                        """.formatted(subjectColumn(kind)))
                .params(token, (double) lease.toMillis() / 1000, id)
                .query((row, n) -> new ClaimedWork(
                        row.getObject("id", UUID.class),
                        kind,
                        row.getObject("subject_id", UUID.class),
                        row.getObject("claim_token", UUID.class),
                        row.getInt("attempts"),
                        row.getInt("max_attempts")))
                .single();
    }

    /** Marks the work done. False if the claim was lost: the caller must then change nothing. */
    public boolean completeIfStillClaimed(ClaimedWork work) {
        return jdbc.sql("""
                        UPDATE pending_work
                        SET status = 'DONE', claim_token = NULL, claimed_until = NULL, last_error = NULL,
                            updated_at = now()
                        WHERE id = ? AND claim_token = ? AND status = 'CLAIMED'
                        """)
                .params(work.id(), work.claimToken())
                .update() == 1;
    }

    /**
     * Schedules another try after {@code backoff}, or marks the work EXHAUSTED
     * when all tries are used. False if the claim was lost.
     */
    public boolean retryLaterIfStillClaimed(ClaimedWork work, String error, Duration backoff) {
        return jdbc.sql("""
                        UPDATE pending_work
                        SET status = CASE WHEN attempts >= max_attempts THEN 'EXHAUSTED' ELSE 'PENDING' END,
                            available_at = now() + make_interval(secs => ?),
                            claim_token = NULL, claimed_until = NULL, last_error = left(?, 500), updated_at = now()
                        WHERE id = ? AND claim_token = ? AND status = 'CLAIMED'
                        """)
                .params((double) backoff.toMillis() / 1000, error, work.id(), work.claimToken())
                .update() == 1;
    }

    /** An operator asks for exhausted work to be tried again. Returns the number of rows re-queued. */
    public int requeueExhausted(WorkKind kind, UUID subjectId) {
        return jdbc.sql("""
                        UPDATE pending_work
                        SET status = 'PENDING', attempts = 0, available_at = now(), updated_at = now()
                        WHERE kind = ? AND %s = ? AND status = 'EXHAUSTED'
                        """.formatted(subjectColumn(kind)))
                .params(kind.name(), subjectId)
                .update();
    }

    public List<WorkView> list(String status) {
        return jdbc.sql("""
                        SELECT id, kind, command_id, artifact_id, status, attempts, max_attempts, last_error
                        FROM pending_work WHERE status = ? ORDER BY created_at LIMIT 100
                        """)
                .param(status)
                .query((row, n) -> new WorkView(
                        row.getObject("id", UUID.class),
                        row.getString("kind"),
                        row.getObject("command_id", UUID.class),
                        row.getObject("artifact_id", UUID.class),
                        row.getString("status"),
                        row.getInt("attempts"),
                        row.getInt("max_attempts"),
                        row.getString("last_error")))
                .list();
    }

    /** The column naming what the work is about. Fixed per kind, never taken from input. */
    private static String subjectColumn(WorkKind kind) {
        return switch (kind) {
            case DISPATCH_START -> "command_id";
            case VERIFY_ARTIFACT -> "artifact_id";
        };
    }
}
