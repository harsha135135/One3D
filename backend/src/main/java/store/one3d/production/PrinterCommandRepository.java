package store.one3d.production;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

interface PrinterCommandRepository extends JpaRepository<PrinterCommand, UUID> {

    Optional<PrinterCommand> findByAttemptId(UUID attemptId);
}
