package store.one3d.orders;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import store.one3d.support.CatalogFixtures;
import store.one3d.support.PostgresIntegrationTest;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * An order keeps the catalog values it was placed with. Each test creates its
 * own catalog item so that changing it cannot affect other tests.
 */
@PostgresIntegrationTest
class OrderSnapshotIT {

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcClient jdbc;

    private CatalogFixtures catalog;

    @BeforeEach
    void setUp() {
        catalog = new CatalogFixtures(jdbc);
    }

    @Test
    void catalogChangesDoNotAlterAnExistingOrderButApplyToNewOrders() throws Exception {
        CatalogFixtures.Item lamp = catalog.orderableItem("Moon Lamp", 120_000, "INR");
        String existingOrderId = placeOrder(lamp.configurationId());

        // Change every catalog value the order copied, then approve recipe
        // revision 2 by moving the configuration's current-recipe pointer to it.
        jdbc.sql("UPDATE product SET name = 'Moon Lamp v2' WHERE id = ?")
                .params(lamp.productId())
                .update();
        jdbc.sql("UPDATE product_configuration SET price_minor = 150000, currency = 'USD' WHERE id = ?")
                .params(lamp.configurationId())
                .update();
        UUID recipeRevision2 = catalog.addRecipeRevision(lamp.configurationId(), 2);
        catalog.setCurrentRecipe(lamp.configurationId(), recipeRevision2);

        // The existing order still has its original name, price, currency and recipe revision.
        mvc.perform(get("/api/orders/{id}", existingOrderId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.line.productName").value("Moon Lamp"))
                .andExpect(jsonPath("$.line.unitPrice.amountMinor").value(120000))
                .andExpect(jsonPath("$.line.unitPrice.currency").value("INR"))
                .andExpect(jsonPath("$.line.recipeRevisionId").value(lamp.recipeRevisionId().toString()))
                .andExpect(jsonPath("$.line.recipeRevisionNumber").value(1))
                .andExpect(jsonPath("$.total.amountMinor").value(120000))
                .andExpect(jsonPath("$.total.currency").value("INR"));

        // A new order for the same configuration gets the updated values.
        String newOrderId = placeOrder(lamp.configurationId());

        mvc.perform(get("/api/orders/{id}", newOrderId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.line.productName").value("Moon Lamp v2"))
                .andExpect(jsonPath("$.line.unitPrice.amountMinor").value(150000))
                .andExpect(jsonPath("$.line.unitPrice.currency").value("USD"))
                .andExpect(jsonPath("$.line.recipeRevisionId").value(recipeRevision2.toString()))
                .andExpect(jsonPath("$.line.recipeRevisionNumber").value(2))
                .andExpect(jsonPath("$.total.amountMinor").value(150000))
                .andExpect(jsonPath("$.total.currency").value("USD"));
    }

    @Test
    void priceCurrencyAndRecipeComeFromTheCatalogNotFromTheRequest() throws Exception {
        CatalogFixtures.Item vase = catalog.orderableItem("Spiral Vase", 80_000, "INR");

        // A client tries to choose its own price, currency and recipe.
        String json = """
                {"productConfigurationId": "%s", "quantity": 1,
                 "unitPriceMinor": 1, "currency": "USD", "recipeRevisionId": "%s",
                 "total": {"amountMinor": 1, "currency": "USD"}}
                """.formatted(vase.configurationId(), UUID.randomUUID());

        mvc.perform(post("/api/orders").contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.line.unitPrice.amountMinor").value(80000))
                .andExpect(jsonPath("$.line.unitPrice.currency").value("INR"))
                .andExpect(jsonPath("$.line.recipeRevisionId").value(vase.recipeRevisionId().toString()))
                .andExpect(jsonPath("$.total.amountMinor").value(80000))
                .andExpect(jsonPath("$.total.currency").value("INR"));
    }

    private String placeOrder(UUID configurationId) throws Exception {
        String body = mvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"productConfigurationId": "%s", "quantity": 1}
                                """.formatted(configurationId)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }
}
