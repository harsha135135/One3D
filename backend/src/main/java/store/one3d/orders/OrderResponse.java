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
        Instant handedOverAt,
        Instant paymentWindowEndsAt,
        Line line,
        Money total) {

    /**
     * {@code source} says where the item came from: CATALOG lines have a
     * {@code productConfigurationId}, CUSTOM_QUOTE lines a {@code quoteId}.
     */
    public record Line(
            LineSource source,
            UUID productConfigurationId,
            UUID quoteId,
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
                order.getHandedOverAt(),
                order.getPaymentWindowEndsAt(),
                new Line(
                        line.getSource(),
                        line.getProductConfigurationId(),
                        line.getQuoteId(),
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
