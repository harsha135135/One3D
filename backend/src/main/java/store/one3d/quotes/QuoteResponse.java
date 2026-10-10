package store.one3d.quotes;

import store.one3d.catalog.RecipeSnapshot;
import store.one3d.common.Money;

import java.time.Instant;
import java.util.UUID;

/**
 * A quote as the API shows it: what it prices (revision, file version and
 * digest), how it will be made (recipe revision), and whether it can still be
 * accepted ({@code acceptable}).
 */
public record QuoteResponse(
        UUID id,
        UUID requestId,
        UUID revisionId,
        Money price,
        QuoteStatus status,
        Instant issuedAt,
        Instant expiresAt,
        boolean expired,
        boolean acceptable,
        Instant acceptedAt,
        Instant withdrawnAt,
        UUID orderId,
        RecipeSnapshot recipe) {

    static QuoteResponse from(Quote quote, RecipeSnapshot recipe, Instant now) {
        boolean expired = quote.isExpired(now);
        return new QuoteResponse(quote.getId(), quote.getRequestId(), quote.getRevisionId(),
                new Money(quote.getPriceMinor(), quote.getCurrency()), quote.getStatus(), quote.getIssuedAt(),
                quote.getExpiresAt(), expired, quote.getStatus() == QuoteStatus.ISSUED && !expired,
                quote.getAcceptedAt(), quote.getWithdrawnAt(), quote.getOrderId(), recipe);
    }
}
