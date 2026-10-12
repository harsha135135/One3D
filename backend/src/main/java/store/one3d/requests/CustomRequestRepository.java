package store.one3d.requests;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

interface CustomRequestRepository extends JpaRepository<CustomRequest, UUID> {

    /** The request only if it belongs to this customer; anyone else's is "not found". */
    Optional<CustomRequest> findByIdAndCustomerId(UUID id, UUID customerId);

    /**
     * Locks the request row ({@code SELECT … FOR UPDATE}). Every change to a
     * request or its quotes takes this lock first, so new revisions, quoting
     * and acceptance for one request happen one at a time, in one lock order.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from CustomRequest r where r.id = :id")
    Optional<CustomRequest> findForUpdate(UUID id);

    Page<CustomRequest> findByCustomerIdOrderByCreatedAtDesc(UUID customerId, Pageable pageable);

    Page<CustomRequest> findByStatusInOrderByUpdatedAtAsc(Collection<RequestStatus> statuses, Pageable pageable);
}
