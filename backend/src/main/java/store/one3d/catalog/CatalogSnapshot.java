package store.one3d.catalog;

import java.util.UUID;

/**
 * The catalog values an order copies at the moment it is placed. This record is
 * the catalog module's public contract; other modules never see its entities.
 */
public record CatalogSnapshot(
        UUID configurationId,
        String productName,
        String configurationCode,
        String configurationDescription,
        UUID recipeRevisionId,
        int recipeRevisionNumber,
        long unitPriceMinor,
        String currency) {
}
