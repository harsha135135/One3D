package store.one3d.production;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface PrinterRepository extends JpaRepository<Printer, UUID> {

    List<Printer> findAllByOrderByName();

    /**
     * Loads the printer with {@code SELECT ... FOR UPDATE}: a second transaction
     * trying to reserve or change the same printer waits until this one commits,
     * then sees its result.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Printer p where p.id = :id")
    Optional<Printer> findForUpdate(UUID id);
}
