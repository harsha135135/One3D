package store.one3d.requests;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import store.one3d.common.PageResponse;
import store.one3d.files.DownloadRedirect;

import java.util.List;
import java.util.UUID;

/** Operator review of custom requests. Quoting is under {@code /api/ops/requests/{id}/quotes}. */
@RestController
@RequestMapping("/api/ops/requests")
class OperatorRequestController {

    private final RequestService requests;

    OperatorRequestController(RequestService requests) {
        this.requests = requests;
    }

    /** Requests in one state (default: waiting for review), longest waiting first. */
    @GetMapping
    PageResponse<RequestSummary> list(@RequestParam(defaultValue = "SUBMITTED") RequestStatus status,
                                      @RequestParam(defaultValue = "0") int page,
                                      @RequestParam(defaultValue = "20") int size) {
        return requests.list(List.of(status), page, size);
    }

    @GetMapping("/{requestId}")
    RequestDetail get(@PathVariable UUID requestId) {
        return requests.get(requestId);
    }

    @PostMapping("/{requestId}/request-changes")
    RequestDetail requestChanges(@PathVariable UUID requestId, @Valid @RequestBody OperatorMessageRequest body) {
        return requests.requestChanges(requestId, body.message());
    }

    @PostMapping("/{requestId}/reject")
    RequestDetail reject(@PathVariable UUID requestId, @Valid @RequestBody OperatorMessageRequest body) {
        return requests.reject(requestId, body.message());
    }

    /** Redirects to a short-lived link for the exact checked file of that revision. */
    @GetMapping("/{requestId}/revisions/{revisionNumber}/file")
    ResponseEntity<Void> revisionFile(@PathVariable UUID requestId, @PathVariable int revisionNumber) {
        return DownloadRedirect.to(requests.revisionFile(requestId, revisionNumber));
    }
}
