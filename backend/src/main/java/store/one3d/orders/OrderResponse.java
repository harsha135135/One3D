package store.one3d.orders;

import store.one3d.common.Money;

import java.time.Instant;
import java.util.UUID;

/** API representation of an order. Built from the entity inside a transaction. */
public record OrderResponse(
        UUID id,
        OrderStatus status,
        Instant createdAt,
        Line line,
        Money total) {

    public record Line(
            UUID productConfigurationId,
            String productName,
            String configurationCode,
            String configurationDescription,
            UUID recipeRevisionId,
            int recipeRevisionNumber,
            int quantity,
            Money unitPrice) {
    }

    static OrderResponse from(CustomerOrder order) {
        OrderLine line = order.getLine();
        return new OrderResponse(
                order.getId(),
                order.getStatus(),
                order.getCreatedAt(),
                new Line(
                        line.getProductConfigurationId(),
                        line.getProductName(),
                        line.getConfigurationCode(),
                        line.getConfigurationDescription(),
                        line.getRecipeRevisionId(),
                        line.getRecipeRevisionNumber(),
                        line.getQuantity(),
                        new Money(line.getUnitPriceMinor(), line.getCurrency())),
                order.total());
    }
}
