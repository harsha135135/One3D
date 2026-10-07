package store.one3d.catalog;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface ProductConfigurationRepository extends JpaRepository<ProductConfiguration, UUID> {

    /**
     * Loads a configuration with its product and current recipe in one query.
     * The inner join on the recipe excludes configurations without an approved
     * recipe, so they are treated as not orderable.
     */
    @Query("""
            select c from ProductConfiguration c
            join fetch c.product
            join fetch c.currentRecipeRevision
            where c.id = :id
            """)
    Optional<ProductConfiguration> findOrderable(UUID id);

    @Query("""
            select c from ProductConfiguration c
            join fetch c.product p
            join fetch c.currentRecipeRevision
            order by p.name, c.code
            """)
    List<ProductConfiguration> findAllOrderable();
}
