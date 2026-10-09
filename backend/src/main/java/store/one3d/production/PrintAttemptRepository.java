package store.one3d.production;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface PrintAttemptRepository extends JpaRepository<PrintAttempt, UUID> {

    /** Loads the attempt together with its job, so job methods receive real entities. */
    @Query("select a from PrintAttempt a join fetch a.job where a.id = :id")
    Optional<PrintAttempt> findWithJob(UUID id);

    /**
     * Commands of running attempts, for the observer. Attempts from before
     * Checkpoint 3 have no command and are never polled.
     */
    @Query("""
            select c.id from PrinterCommand c, PrintAttempt a
            where c.attemptId = a.id and a.status = store.one3d.production.AttemptStatus.RUNNING
            order by a.startedAt
            """)
    List<UUID> commandsOfRunningAttempts();
}
