package store.one3d.quotes;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface QuoteRepository extends JpaRepository<Quote, UUID> {

    /** Locks the quote row. Callers lock its request first (see RequestService), then the quote. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select q from Quote q where q.id = :id")
    Optional<Quote> findForUpdate(UUID id);

    /**
     * Only the request id, without loading the quote: the quote must first be
     * read by {@link #findForUpdate} after the request lock, or the session
     * would keep returning the state read before the lock.
     */
    @Query("select q.requestId from Quote q where q.id = :id")
    Optional<UUID> findRequestIdById(UUID id);

    Optional<Quote> findByRequestIdAndStatus(UUID requestId, QuoteStatus status);

    List<Quote> findByRequestIdOrderByIssuedAtDesc(UUID requestId);
}
