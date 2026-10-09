package store.one3d.orders;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import store.one3d.support.CatalogFixtures;
import store.one3d.support.PostgresIntegrationTest;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** HTTP behaviour of /api/orders, including validation and error responses. */
@PostgresIntegrationTest
class OrderApiIT {

    static final String DEMO_CONFIGURATION_ID = "0d3d0000-0000-4000-8000-000000000002";
    static final String DEMO_RECIPE_REVISION_ID = "0d3d0000-0000-4000-8000-000000000003";

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcClient jdbc;

    @Test
    void placesAnOrderForTheDemoConfigurationAndReadsItBack() throws Exception {
        String created = placeOrder("""
                {"productConfigurationId": "%s", "quantity": 1}
                """.formatted(DEMO_CONFIGURATION_ID))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", matchesPattern(".*/api/orders/[0-9a-f-]{36}")))
                .andExpect(jsonPath("$.status").value("AWAITING_PAYMENT"))
                .andExpect(jsonPath("$.line.productConfigurationId").value(DEMO_CONFIGURATION_ID))
                .andExpect(jsonPath("$.line.productName").value("Hex Desk Organizer (demo)"))
                .andExpect(jsonPath("$.line.configurationCode").value("HEX-ORG-PLA-BLACK"))
                .andExpect(jsonPath("$.line.recipeRevisionId").value(DEMO_RECIPE_REVISION_ID))
                .andExpect(jsonPath("$.line.recipeRevisionNumber").value(1))
                .andExpect(jsonPath("$.line.quantity").value(1))
                .andExpect(jsonPath("$.line.unitPrice.amountMinor").value(49900))
                .andExpect(jsonPath("$.line.unitPrice.currency").value("INR"))
                .andExpect(jsonPath("$.total.amountMinor").value(49900))
                .andExpect(jsonPath("$.total.currency").value("INR"))
                .andReturn().getResponse().getContentAsString();

        String orderId = JsonPath.read(created, "$.id");

        // The stored order, read back from PostgreSQL, matches what POST returned.
        mvc.perform(get("/api/orders/{id}", orderId))
                .andExpect(status().isOk())
                .andExpect(content().json(created, JsonCompareMode.STRICT));
    }

    @Test
    void missingConfigurationIdIsRejected() throws Exception {
        placeOrder("""
                {"quantity": 1}
                """)
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value("productConfigurationId"));
    }

    @Test
    void missingQuantityDefaultsToOne() throws Exception {
        String created = placeOrder("""
                {"productConfigurationId": "%s"}
                """.formatted(DEMO_CONFIGURATION_ID))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.line.quantity").value(1))
                .andExpect(jsonPath("$.total.amountMinor").value(49900))
                .andReturn().getResponse().getContentAsString();

        String orderId = JsonPath.read(created, "$.id");

        mvc.perform(get("/api/orders/{id}", orderId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.line.quantity").value(1));
    }

    @Test
    void explicitNullQuantityDefaultsToOne() throws Exception {
        placeOrder("""
                {"productConfigurationId": "%s", "quantity": null}
                """.formatted(DEMO_CONFIGURATION_ID))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.line.quantity").value(1));
    }

    @Test
    void zeroQuantityIsRejectedAndNothingIsStored() throws Exception {
        long ordersBefore = countOrders();

        placeOrder("""
                {"productConfigurationId": "%s", "quantity": 0}
                """.formatted(DEMO_CONFIGURATION_ID))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value("quantity"));

        assertThat(countOrders()).isEqualTo(ordersBefore);
    }

    @ParameterizedTest
    @ValueSource(strings = {"1.5", "0.5", "1.0", "1e0"})
    void nonIntegerQuantityIsRejectedAndNothingIsStored(String quantity) throws Exception {
        // JSON numbers with a fraction or exponent must not be truncated to 1.
        long ordersBefore = countOrders();

        placeOrder("""
                {"productConfigurationId": "%s", "quantity": %s}
                """.formatted(DEMO_CONFIGURATION_ID, quantity))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value("quantity"));

        assertThat(countOrders()).isEqualTo(ordersBefore);
    }

    @Test
    void quantityTwoIsRejectedAndNothingIsStored() throws Exception {
        long ordersBefore = countOrders();

        placeOrder("""
                {"productConfigurationId": "%s", "quantity": 2}
                """.formatted(DEMO_CONFIGURATION_ID))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value("quantity"))
                .andExpect(jsonPath("$.errors[0].message")
                        .value(containsString("must be 1")));

        assertThat(countOrders()).isEqualTo(ordersBefore);
    }

    @Test
    void malformedJsonIsRejected() throws Exception {
        placeOrder("{\"productConfigurationId\": ")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
    }

    @Test
    void unknownConfigurationIsUnprocessable() throws Exception {
        placeOrder("""
                {"productConfigurationId": "%s", "quantity": 1}
                """.formatted(UUID.randomUUID()))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("CONFIGURATION_NOT_ORDERABLE"));
    }

    @Test
    void configurationWithoutAnApprovedRecipeIsUnprocessable() throws Exception {
        CatalogFixtures.Item draft = new CatalogFixtures(jdbc).itemWithoutRecipe("Draft Item", 5_000, "INR");

        placeOrder("""
                {"productConfigurationId": "%s", "quantity": 1}
                """.formatted(draft.configurationId()))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("CONFIGURATION_NOT_ORDERABLE"));
    }

    @Test
    void unknownOrderIsNotFound() throws Exception {
        mvc.perform(get("/api/orders/{id}", UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ORDER_NOT_FOUND"));
    }

    @Test
    void orderIdThatIsNotAUuidIsRejected() throws Exception {
        mvc.perform(get("/api/orders/{id}", "not-a-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PARAMETER"));
    }

    private ResultActions placeOrder(String json) throws Exception {
        return mvc.perform(post("/api/orders").contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private long countOrders() {
        return jdbc.sql("SELECT count(*) FROM customer_order").query(Long.class).single();
    }
}
