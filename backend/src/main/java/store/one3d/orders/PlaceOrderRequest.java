package store.one3d.orders;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * Body of POST /api/orders. The client chooses only what to buy. Price, currency
 * and recipe come from the catalog on the server; any such fields in the
 * request JSON are ignored.
 */
record PlaceOrderRequest(
        @NotNull
        UUID productConfigurationId,

        @NotNull
        @Min(value = CustomerOrder.V1_QUANTITY, message = "must be 1: a V1 order contains exactly one item")
        @Max(value = CustomerOrder.V1_QUANTITY, message = "must be 1: a V1 order contains exactly one item")
        Integer quantity) {
}
