package store.one3d.orders;

import org.junit.jupiter.api.Test;
import store.one3d.catalog.CatalogSnapshot;
import store.one3d.common.Money;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Unit tests for the order's own rules; no Spring context or database. */
class CustomerOrderTest {

    private static final Instant NOW = Instant.parse("2026-10-07T08:00:00Z");

    private static final CatalogSnapshot ITEM = new CatalogSnapshot(
            UUID.fromString("00000000-0000-4000-8000-00000000000a"),
            "Hex Desk Organizer",
            "HEX-ORG",
            "Matte black PLA+",
            UUID.fromString("00000000-0000-4000-8000-00000000000b"),
            3,
            49_900,
            "INR");

    @Test
    void newOrderAwaitsPaymentAndCopiesTheCatalogValues() {
        CustomerOrder order = CustomerOrder.place(ITEM, 1, NOW);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.AWAITING_PAYMENT);
        assertThat(order.getCreatedAt()).isEqualTo(NOW);

        OrderLine line = order.getLine();
        assertThat(line.getProductConfigurationId()).isEqualTo(ITEM.configurationId());
        assertThat(line.getProductName()).isEqualTo("Hex Desk Organizer");
        assertThat(line.getConfigurationCode()).isEqualTo("HEX-ORG");
        assertThat(line.getRecipeRevisionId()).isEqualTo(ITEM.recipeRevisionId());
        assertThat(line.getRecipeRevisionNumber()).isEqualTo(3);
        assertThat(line.getQuantity()).isEqualTo(1);
        assertThat(line.getUnitPriceMinor()).isEqualTo(49_900);
        assertThat(line.getCurrency()).isEqualTo("INR");
    }

    @Test
    void totalIsUnitPriceTimesQuantityInTheLineCurrency() {
        assertThat(CustomerOrder.place(ITEM, 1, NOW).total()).isEqualTo(new Money(49_900, "INR"));
    }

    @Test
    void quantityOtherThanOneIsRejectedEvenIfRequestValidationIsBypassed() {
        assertThatThrownBy(() -> CustomerOrder.place(ITEM, 2, NOW))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("quantity 1");
        assertThatThrownBy(() -> CustomerOrder.place(ITEM, 0, NOW))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
