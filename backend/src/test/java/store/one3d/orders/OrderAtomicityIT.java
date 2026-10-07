package store.one3d.orders;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import store.one3d.support.CatalogFixtures;
import store.one3d.support.PostgresIntegrationTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Proves that placing an order is atomic. A temporary trigger makes the
 * order_line INSERT fail for one test configuration; the customer_order INSERT
 * that ran just before it in the same transaction must be rolled back too.
 */
@PostgresIntegrationTest
class OrderAtomicityIT {

    private static final String INJECTED_FAILURE = "injected order_line failure";

    @Autowired
    OrderService orderService;

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcClient jdbc;

    private CatalogFixtures.Item faultyItem;

    @BeforeEach
    void injectOrderLineFailure() {
        faultyItem = new CatalogFixtures(jdbc).orderableItem("Fault Injection Bowl", 50_000, "INR");

        // The function raises only if the parent customer_order row is already
        // visible, i.e. the order INSERT has succeeded earlier in the same
        // transaction. Otherwise the failure would not test a partial write.
        jdbc.sql("""
                CREATE FUNCTION test_reject_order_line() RETURNS trigger LANGUAGE plpgsql AS $$
                BEGIN
                    IF EXISTS (SELECT 1 FROM customer_order WHERE id = NEW.order_id) THEN
                        RAISE EXCEPTION '%s';
                    END IF;
                    RETURN NEW;
                END
                $$
                """.formatted(INJECTED_FAILURE)).update();
        jdbc.sql("""
                CREATE TRIGGER test_reject_order_line BEFORE INSERT ON order_line
                FOR EACH ROW WHEN (NEW.product_configuration_id = '%s')
                EXECUTE FUNCTION test_reject_order_line()
                """.formatted(faultyItem.configurationId())).update();
    }

    @AfterEach
    void removeInjectedFailure() {
        jdbc.sql("DROP TRIGGER IF EXISTS test_reject_order_line ON order_line").update();
        jdbc.sql("DROP FUNCTION IF EXISTS test_reject_order_line()").update();
    }

    @Test
    void failedLineInsertRollsBackTheOrderInsert() {
        long ordersBefore = countOrders();

        assertThatThrownBy(() -> orderService.placeOrder(faultyItem.configurationId(), 1))
                .rootCause()
                .hasMessageContaining(INJECTED_FAILURE);

        assertThat(countOrders()).isEqualTo(ordersBefore);
        assertThat(countOrdersWithoutALine()).isZero();
    }

    @Test
    void apiReturnsAGenericErrorAndStoresNothing() throws Exception {
        long ordersBefore = countOrders();

        mvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"productConfigurationId": "%s", "quantity": 1}
                                """.formatted(faultyItem.configurationId())))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.detail").value("An unexpected error occurred."))
                .andExpect(content().string(not(containsString("order_line"))));

        assertThat(countOrders()).isEqualTo(ordersBefore);
        assertThat(countOrdersWithoutALine()).isZero();
    }

    private long countOrders() {
        return jdbc.sql("SELECT count(*) FROM customer_order").query(Long.class).single();
    }

    private long countOrdersWithoutALine() {
        return jdbc.sql("""
                        SELECT count(*) FROM customer_order o
                        WHERE NOT EXISTS (SELECT 1 FROM order_line l WHERE l.order_id = o.id)
                        """)
                .query(Long.class)
                .single();
    }
}
