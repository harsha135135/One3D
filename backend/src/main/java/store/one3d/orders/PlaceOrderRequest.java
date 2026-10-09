package store.one3d.orders;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * Body of POST /api/orders. The client chooses only what to buy.
 *
 * <p>V1 supports exactly one item per order. If quantity is omitted or is
 * explicitly {@code null} in the JSON, it defaults to 1; any value other than 1
 * is rejected with 400.
 *
 * <p>Price, currency and recipe come from the catalog on the server; any such
 * fields in the request JSON are ignored.
 */
record PlaceOrderRequest(
        @NotNull
        UUID productConfigurationId,

        @Min(value = CustomerOrder.V1_QUANTITY, message = "must be 1: a V1 order contains exactly one item")
        @Max(value = CustomerOrder.V1_QUANTITY, message = "must be 1: a V1 order contains exactly one item")
        Integer quantity) {

    PlaceOrderRequest {
        if (quantity == null) {
            quantity = CustomerOrder.V1_QUANTITY;
        }
    }
}
