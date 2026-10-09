package store.one3d.orders;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

interface OrderRepository extends JpaRepository<CustomerOrder, UUID> {

    /** Loads an order and its line with one SQL join instead of two queries. */
    @Query("select o from CustomerOrder o join fetch o.line where o.id = :id")
    Optional<CustomerOrder> findWithLine(UUID id);

    /**
     * The order only if it belongs to this customer. Ownership is part of the
     * query, so another customer's order is simply "not found".
     */
    @Query("select o from CustomerOrder o join fetch o.line where o.id = :id and o.customerId = :customerId")
    Optional<CustomerOrder> findOwnedWithLine(UUID id, UUID customerId);

    /** A customer's orders, newest first; the line is fetched in the same query. */
    @EntityGraph(attributePaths = "line")
    Page<CustomerOrder> findByCustomerIdOrderByCreatedAtDesc(UUID customerId, Pageable pageable);

    /** Orders in the given states, oldest first, for the operator queue. */
    @EntityGraph(attributePaths = "line")
    Page<CustomerOrder> findByStatusInOrderByCreatedAtAsc(Collection<OrderStatus> statuses, Pageable pageable);
}
