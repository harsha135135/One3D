package store.one3d.production;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

interface ProductionJobRepository extends JpaRepository<ProductionJob, UUID> {

    Optional<ProductionJob> findByOrderId(UUID orderId);

    boolean existsByOrderId(UUID orderId);
}
