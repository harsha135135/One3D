package store.one3d.catalog;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import store.one3d.support.CatalogFixtures;
import store.one3d.support.PostgresIntegrationTest;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@PostgresIntegrationTest
class CatalogApiIT {

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcClient jdbc;

    @Test
    void listsTheSeededDemoConfigurationWithItsPriceAndRecipeRevision() throws Exception {
        String demo = "$[?(@.configurationCode == 'HEX-ORG-PLA-BLACK')]";

        mvc.perform(get("/api/catalog/configurations"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(demo + ".configurationId", contains("0d3d0000-0000-4000-8000-000000000002")))
                .andExpect(jsonPath(demo + ".productName", contains("Hex Desk Organizer (demo)")))
                .andExpect(jsonPath(demo + ".price.amountMinor", contains(49900)))
                .andExpect(jsonPath(demo + ".price.currency", contains("INR")))
                .andExpect(jsonPath(demo + ".recipeRevisionNumber", contains(1)));
    }

    @Test
    void doesNotListConfigurationsWithoutAnApprovedRecipe() throws Exception {
        CatalogFixtures.Item draft = new CatalogFixtures(jdbc).itemWithoutRecipe("Draft Item", 5_000, "INR");

        mvc.perform(get("/api/catalog/configurations"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.configurationId == '%s')]".formatted(draft.configurationId()), empty()));
    }
}
