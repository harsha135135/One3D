package store.one3d;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;
import store.one3d.support.CatalogFixtures;
import store.one3d.support.PostgresIntegrationTest;
import store.one3d.support.TestUsers;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Writes rows with plain SQL, bypassing the Java code, to show that PostgreSQL
 * itself enforces the core rules. @Transactional rolls each test back.
 */
@PostgresIntegrationTest
@Transactional
class DatabaseConstraintsIT {

    @Autowired
    JdbcClient jdbc;

    private CatalogFixtures catalog;
    private CatalogFixtures.Item item;

    @BeforeEach
    void createCatalogItem() {
        catalog = new CatalogFixtures(jdbc);
        item = catalog.orderableItem("Constraint Test Cube", 10_000, "INR");
    }

    @Test
    void anOrderCannotHaveASecondLine() {
        UUID orderId = insertOrder("AWAITING_PAYMENT");
        insertLine(orderId, item.recipeRevisionId(), 1);

        assertThatThrownBy(() -> insertLine(orderId, item.recipeRevisionId(), 1))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("order_line_order_unique");
    }

    @Test
    void lineQuantityMustBeOne() {
        UUID orderId = insertOrder("AWAITING_PAYMENT");

        assertThatThrownBy(() -> insertLine(orderId, item.recipeRevisionId(), 2))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("order_line_quantity_is_one");
    }

    @Test
    void lineQuantityCannotBeNull() {
        // CHECK (quantity = 1) is not false for NULL, so NOT NULL is needed as well.
        UUID orderId = insertOrder("AWAITING_PAYMENT");

        assertThatThrownBy(() -> insertLine(orderId, item.recipeRevisionId(), null))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("violates not-null constraint");
    }

    @Test
    void lineRecipeMustBelongToTheLineConfiguration() {
        UUID otherRecipe = catalog.orderableItem("Other Item", 20_000, "INR").recipeRevisionId();
        UUID orderId = insertOrder("AWAITING_PAYMENT");

        assertThatThrownBy(() -> insertLine(orderId, otherRecipe, 1))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("order_line_recipe_of_configuration_fk");
    }

    @Test
    void currentRecipeMustBelongToTheConfiguration() {
        UUID otherRecipe = catalog.orderableItem("Other Item", 20_000, "INR").recipeRevisionId();

        assertThatThrownBy(() -> catalog.setCurrentRecipe(item.configurationId(), otherRecipe))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("product_configuration_current_recipe_fk");
    }

    @Test
    void catalogPriceMustBePositive() {
        assertThatThrownBy(() -> jdbc.sql("UPDATE product_configuration SET price_minor = 0 WHERE id = ?")
                .params(item.configurationId())
                .update())
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("product_configuration_price_positive");
    }

    @Test
    void orderStatusMustBeKnown() {
        // Since V3 a status past AWAITING_PAYMENT also needs confirmation
        // fields; they are set here so that only the status rule is broken.
        assertThatThrownBy(() -> jdbc.sql("""
                        INSERT INTO customer_order (id, customer_id, status, created_at, confirmed_at, confirmation_source)
                        VALUES (?, ?, 'SHIPPED', now(), now(), 'DEMO_SIMULATED')
                        """)
                .params(UUID.randomUUID(), TestUsers.LEGACY_CUSTOMER_ID)
                .update())
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("customer_order_status_known");
    }

    private UUID insertOrder(String status) {
        UUID orderId = UUID.randomUUID();
        jdbc.sql("INSERT INTO customer_order (id, customer_id, status, created_at) VALUES (?, ?, ?, ?)")
                .params(orderId, TestUsers.LEGACY_CUSTOMER_ID, status, OffsetDateTime.now())
                .update();
        return orderId;
    }

    private void insertLine(UUID orderId, UUID recipeRevisionId, Integer quantity) {
        jdbc.sql("""
                        INSERT INTO order_line (id, order_id, source, product_configuration_id, recipe_revision_id,
                                                product_name, configuration_code, configuration_description,
                                                recipe_revision_number, quantity, unit_price_minor, currency)
                        VALUES (?, ?, 'CATALOG', ?, ?, 'Constraint Test Cube', 'TEST', 'Test', 1, ?, 10000, 'INR')
                        """)
                .params(UUID.randomUUID(), orderId, item.configurationId(), recipeRevisionId, quantity)
                .update();
    }
}
