package store.one3d.production;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

interface BedClearanceRepository extends JpaRepository<BedClearance, UUID> {

    /** The clearance waiting for the printer's next start (at most one, by a unique index). */
    Optional<BedClearance> findByPrinterIdAndConsumedByAttemptIdIsNull(UUID printerId);

    Optional<BedClearance> findByConsumedByAttemptId(UUID attemptId);
}
