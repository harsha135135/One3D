package store.one3d.orders;

import java.time.Instant;
import java.util.UUID;

/**
 * What an order copies from an accepted quote: the quote, the recipe revision
 * it priced, and display values that fit the order line's snapshot columns
 * (name up to 120 characters, code up to 40, description up to 200). The
 * payment window ends when the accepted quote would have expired.
 */
public record QuotedItem(
        UUID quoteId,
        UUID recipeRevisionId,
        int recipeRevisionNumber,
        String itemName,
        String itemCode,
        String itemDescription,
        long unitPriceMinor,
        String currency,
        Instant paymentWindowEndsAt) {
}
