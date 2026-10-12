package store.one3d.quotes;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import store.one3d.catalog.RecipeService;
import store.one3d.catalog.RecipeSnapshot;
import store.one3d.common.NotFoundException;
import store.one3d.common.Timestamps;
import store.one3d.orders.OrderResponse;
import store.one3d.orders.OrderService;
import store.one3d.orders.QuotedItem;
import store.one3d.requests.QuotableRevision;
import store.one3d.requests.RequestNotFoundException;
import store.one3d.requests.RequestService;
import store.one3d.requests.RevisionSubmitted;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Manual quotes for custom requests, and their acceptance. Every change locks
 * the request first and then the quote, so quoting, a new revision and
 * acceptance for one request never interleave.
 */
@Service
public class QuoteService {

    /** V1 prices in Indian rupees only. */
    static final String CURRENCY = "INR";
    private static final String NAME_PREFIX = "Custom part: ";
    /** order_line.product_name is varchar(120). */
    private static final int MAX_ITEM_NAME = 120;

    private final QuoteRepository quotes;
    private final RequestService requests;
    private final RecipeService recipes;
    private final OrderService orders;

    QuoteService(QuoteRepository quotes, RequestService requests, RecipeService recipes, OrderService orders) {
        this.quotes = quotes;
        this.requests = requests;
        this.recipes = recipes;
        this.orders = orders;
    }

    /**
     * Prices the latest revision of a request. An open quote for the request
     * is withdrawn first; the new quote gets its own recipe revision.
     */
    @Transactional
    public QuoteResponse issue(UUID requestId, IssueQuoteRequest request) {
        QuotableRevision revision = requests.lockForQuote(requestId, request.revisionId());
        Instant now = Timestamps.now();
        quotes.findByRequestIdAndStatus(requestId, QuoteStatus.ISSUED).ifPresent(open -> {
            open.withdraw(now);
            // Written before the new quote's INSERT: at most one ISSUED quote per request.
            quotes.flush();
        });
        // The recipe row references the quote, so the quote is written first.
        Quote quote = quotes.saveAndFlush(Quote.issue(requestId, revision.revisionId(), request.priceMinor(), CURRENCY,
                now, Duration.ofDays(request.validDays())));
        RecipeSnapshot recipe = recipes.createForQuote(quote.getId(), request.material(),
                request.layerHeightMicrons(), request.recipeNotes());
        requests.markQuoted(requestId);
        return QuoteResponse.from(quote, recipe, now);
    }

    /**
     * Accepts the customer's quote and creates its order, in one transaction.
     * Accepting again returns the same order; a withdrawn, expired or outdated
     * quote is refused.
     */
    @Transactional
    public OrderResponse accept(UUID customerId, UUID quoteId) {
        UUID requestId = quotes.findRequestIdById(quoteId).orElseThrow(() -> quoteNotFound(quoteId));
        try {
            requests.lockForAcceptance(customerId, requestId);
        } catch (RequestNotFoundException e) {
            throw quoteNotFound(quoteId); // someone else's quote is "not found"
        }
        Quote quote = quotes.findForUpdate(quoteId).orElseThrow();
        if (quote.getStatus() == QuoteStatus.ACCEPTED) {
            return orders.getOwnOrder(customerId, quote.getOrderId());
        }
        Instant now = Timestamps.now();
        quote.requireAcceptable(now);
        QuotableRevision revision = requests.requireAwaitingAcceptance(requestId, quote.getRevisionId());
        RecipeSnapshot recipe = recipes.forQuote(quoteId).orElseThrow();

        OrderResponse order = orders.placeFromQuote(customerId, new QuotedItem(
                quote.getId(),
                recipe.id(),
                recipe.revisionNumber(),
                itemName(revision.filename()),
                "CUSTOM-" + quote.getId().toString().substring(0, 8),
                revision.material() + ", " + revision.colour() + " (revision " + revision.revisionNumber() + ")",
                quote.getPriceMinor(),
                quote.getCurrency(),
                quote.getExpiresAt()));
        quote.accept(order.id(), now);
        requests.markOrdered(requestId);
        return order;
    }

    /** All quotes of the customer's own request, newest first. */
    @Transactional(readOnly = true)
    public List<QuoteResponse> listOwn(UUID customerId, UUID requestId) {
        requests.requireOwned(customerId, requestId);
        return list(requestId);
    }

    /** All quotes of a request, newest first, for operators. */
    @Transactional(readOnly = true)
    public List<QuoteResponse> list(UUID requestId) {
        List<Quote> found = quotes.findByRequestIdOrderByIssuedAtDesc(requestId);
        Map<UUID, RecipeSnapshot> recipesByQuote = recipes.forQuotes(found.stream().map(Quote::getId).toList());
        Instant now = Timestamps.now();
        return found.stream().map(quote -> QuoteResponse.from(quote, recipesByQuote.get(quote.getId()), now)).toList();
    }

    /**
     * A new revision means different content, so no earlier price applies.
     * Runs synchronously inside the transaction that stored the revision.
     */
    @EventListener
    void withdrawOnNewRevision(RevisionSubmitted event) {
        quotes.findByRequestIdAndStatus(event.requestId(), QuoteStatus.ISSUED)
                .ifPresent(open -> open.withdraw(Timestamps.now()));
    }

    /** "Custom part: " plus the file name, shortened to fit order_line.product_name. */
    static String itemName(String filename) {
        String name = NAME_PREFIX + filename;
        // File names are ASCII after sanitizing, so cutting by char cannot split a character.
        return name.length() <= MAX_ITEM_NAME ? name : name.substring(0, MAX_ITEM_NAME - 1) + "…";
    }

    private static NotFoundException quoteNotFound(UUID quoteId) {
        return new NotFoundException("QUOTE_NOT_FOUND", "Quote " + quoteId + " was not found.");
    }
}
