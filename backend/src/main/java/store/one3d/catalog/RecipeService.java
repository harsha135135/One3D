package store.one3d.catalog;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import store.one3d.common.Timestamps;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Recipes owned by quotes. The catalog module owns every recipe revision, so
 * the quotes module asks here instead of writing the table itself.
 */
@Service
public class RecipeService {

    private final RecipeRevisionRepository recipes;

    RecipeService(RecipeRevisionRepository recipes) {
        this.recipes = recipes;
    }

    /**
     * Records the recipe a quote prices. Joins the caller's transaction; the
     * quote row must already be in the database (the recipe references it).
     */
    @Transactional
    public RecipeSnapshot createForQuote(UUID quoteId, String material, int layerHeightMicrons, String notes) {
        return recipes.save(RecipeRevision.forQuote(quoteId, material, layerHeightMicrons, notes, Timestamps.now()))
                .toSnapshot();
    }

    @Transactional(readOnly = true)
    public Optional<RecipeSnapshot> forQuote(UUID quoteId) {
        return recipes.findByQuoteId(quoteId).map(RecipeRevision::toSnapshot);
    }

    /** Recipes by quote id, for building lists. */
    @Transactional(readOnly = true)
    public Map<UUID, RecipeSnapshot> forQuotes(Collection<UUID> quoteIds) {
        return recipes.findByQuoteIdIn(quoteIds).stream()
                .collect(Collectors.toMap(RecipeRevision::getQuoteId, RecipeRevision::toSnapshot));
    }
}
