package store.one3d.quotes;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import store.one3d.identity.CurrentUser;
import store.one3d.orders.OrderResponse;

import java.util.List;
import java.util.UUID;

/** The signed-in customer's quotes. */
@RestController
class QuoteController {

    private final QuoteService quotes;
    private final CurrentUser currentUser;

    QuoteController(QuoteService quotes, CurrentUser currentUser) {
        this.quotes = quotes;
        this.currentUser = currentUser;
    }

    @GetMapping("/api/requests/{requestId}/quotes")
    List<QuoteResponse> list(@PathVariable UUID requestId) {
        return quotes.listOwn(currentUser.id(), requestId);
    }

    /** Creates the order for this quote, or returns the order it already created. */
    @PostMapping("/api/quotes/{quoteId}/accept")
    OrderResponse accept(@PathVariable UUID quoteId) {
        return quotes.accept(currentUser.id(), quoteId);
    }
}
