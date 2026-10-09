package store.one3d.production;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;
import java.util.UUID;

interface PrintAttemptRepository extends JpaRepository<PrintAttempt, UUID> {

    /** Loads the attempt together with its job, so job methods receive real entities. */
    @Query("select a from PrintAttempt a join fetch a.job where a.id = :id")
    Optional<PrintAttempt> findWithJob(UUID id);
}
