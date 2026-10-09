package store.one3d.orders;

import store.one3d.common.Money;

import java.time.Instant;
import java.util.UUID;

/** API representation of an order. Built from the entity inside a transaction. */
public record OrderResponse(
        UUID id,
        UUID customerId,
        OrderStatus status,
        Instant createdAt,
        Instant confirmedAt,
        ConfirmationSource confirmationSource,
        Instant readyForHandoffAt,
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
                order.getCustomerId(),
                order.getStatus(),
                order.getCreatedAt(),
                order.getConfirmedAt(),
                order.getConfirmationSource(),
                order.getReadyForHandoffAt(),
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
