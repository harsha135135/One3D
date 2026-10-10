package store.one3d.requests;

import store.one3d.common.NotFoundException;

import java.util.UUID;

/** No such request, or it belongs to someone else (which is not revealed). */
public class RequestNotFoundException extends NotFoundException {

    public RequestNotFoundException(UUID requestId) {
        super("REQUEST_NOT_FOUND", "Request " + requestId + " was not found.");
    }
}
