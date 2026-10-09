package store.one3d.support;

import org.springframework.jdbc.core.simple.JdbcClient;

import java.util.Locale;
import java.util.UUID;

/**
 * Inserts catalog rows with plain SQL. Each fixture uses fresh ids and codes,
 * so tests never modify the seeded demo catalog or each other's data.
 */
public final class CatalogFixtures {

    /** Ids of a product, its configuration and recipe revision 1. */
    public record Item(UUID productId, UUID configurationId, UUID recipeRevisionId) {
    }

    private final JdbcClient jdbc;

    public CatalogFixtures(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** A configuration with recipe revision 1 approved as its current recipe. */
    public Item orderableItem(String productName, long priceMinor, String currency) {
        Item item = itemWithoutRecipe(productName, priceMinor, currency);
        UUID recipeId = addRecipeRevision(item.configurationId(), 1);
        setCurrentRecipe(item.configurationId(), recipeId);
        return new Item(item.productId(), item.configurationId(), recipeId);
    }

    /** A configuration that has no approved recipe yet, so it cannot be ordered. */
    public Item itemWithoutRecipe(String productName, long priceMinor, String currency) {
        UUID productId = UUID.randomUUID();
        UUID configurationId = UUID.randomUUID();
        String code = "TEST-" + configurationId.toString().substring(0, 8).toUpperCase(Locale.ROOT);

        jdbc.sql("INSERT INTO product (id, name) VALUES (?, ?)")
                .params(productId, productName)
                .update();
        jdbc.sql("""
                        INSERT INTO product_configuration (id, product_id, code, description, price_minor, currency)
                        VALUES (?, ?, ?, ?, ?, ?)
                        """)
                .params(configurationId, productId, code, "Test configuration", priceMinor, currency)
                .update();
        return new Item(productId, configurationId, null);
    }

    public UUID addRecipeRevision(UUID configurationId, int revisionNumber) {
        UUID recipeId = UUID.randomUUID();
        jdbc.sql("""
                        INSERT INTO recipe_revision (id, product_configuration_id, revision_number,
                                                     material, layer_height_microns, notes)
                        VALUES (?, ?, ?, 'Test PLA+', 200, 'Test recipe')
                        """)
                .params(recipeId, configurationId, revisionNumber)
                .update();
        return recipeId;
    }

    public void setCurrentRecipe(UUID configurationId, UUID recipeRevisionId) {
        jdbc.sql("UPDATE product_configuration SET current_recipe_revision_id = ? WHERE id = ?")
                .params(recipeRevisionId, configurationId)
                .update();
    }
}
