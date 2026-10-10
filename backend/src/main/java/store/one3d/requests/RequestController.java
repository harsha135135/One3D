package store.one3d.requests;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import store.one3d.common.PageResponse;
import store.one3d.identity.CurrentUser;

import java.util.UUID;

/** The signed-in customer's custom requests. Quotes are under {@code /api/requests/{id}/quotes}. */
@RestController
@RequestMapping("/api/requests")
class RequestController {

    private final RequestService requests;
    private final CurrentUser currentUser;

    RequestController(RequestService requests, CurrentUser currentUser) {
        this.requests = requests;
        this.currentUser = currentUser;
    }

    @PostMapping
    ResponseEntity<RequestDetail> submit(@Valid @RequestBody Requirements requirements) {
        return ResponseEntity.status(HttpStatus.CREATED).body(requests.submit(currentUser.id(), requirements));
    }

    @GetMapping
    PageResponse<RequestSummary> list(@RequestParam(defaultValue = "0") int page,
                                      @RequestParam(defaultValue = "20") int size) {
        return requests.listOwn(currentUser.id(), page, size);
    }

    @GetMapping("/{requestId}")
    RequestDetail get(@PathVariable UUID requestId) {
        return requests.getOwn(currentUser.id(), requestId);
    }

    /** A new file and requirements; review starts again and any open quote is withdrawn. */
    @PostMapping("/{requestId}/revisions")
    ResponseEntity<RequestDetail> revise(@PathVariable UUID requestId, @Valid @RequestBody Requirements requirements) {
        return ResponseEntity.status(HttpStatus.CREATED).body(requests.revise(currentUser.id(), requestId, requirements));
    }
}
