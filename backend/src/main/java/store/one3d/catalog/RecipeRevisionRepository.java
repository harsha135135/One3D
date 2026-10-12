package store.one3d.catalog;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface RecipeRevisionRepository extends JpaRepository<RecipeRevision, UUID> {

    Optional<RecipeRevision> findByQuoteId(UUID quoteId);

    List<RecipeRevision> findByQuoteIdIn(Collection<UUID> quoteIds);
}
