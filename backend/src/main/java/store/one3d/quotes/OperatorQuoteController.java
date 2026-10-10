package store.one3d.quotes;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** Operators price custom requests. Preparing the model for printing stays manual, outside the application. */
@RestController
@RequestMapping("/api/ops/requests/{requestId}/quotes")
class OperatorQuoteController {

    private final QuoteService quotes;

    OperatorQuoteController(QuoteService quotes) {
        this.quotes = quotes;
    }

    @GetMapping
    List<QuoteResponse> list(@PathVariable UUID requestId) {
        return quotes.list(requestId);
    }

    /** Quotes the latest revision; an open quote for the request is withdrawn. */
    @PostMapping
    ResponseEntity<QuoteResponse> issue(@PathVariable UUID requestId, @Valid @RequestBody IssueQuoteRequest body) {
        return ResponseEntity.status(HttpStatus.CREATED).body(quotes.issue(requestId, body));
    }
}
