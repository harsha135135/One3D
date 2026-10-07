package store.one3d.orders;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;
import java.util.UUID;

interface OrderRepository extends JpaRepository<CustomerOrder, UUID> {

    /** Loads an order and its line with one SQL join instead of two queries. */
    @Query("select o from CustomerOrder o join fetch o.line where o.id = :id")
    Optional<CustomerOrder> findWithLine(UUID id);
}
