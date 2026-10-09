package store.one3d.common;

import org.springframework.http.HttpStatus;

/**
 * The request is valid, but the current state does not allow it, for example
 * starting a print while the bed is still occupied. Returned as 409 Conflict.
 */
public class ConflictException extends ApiException {

    public ConflictException(String code, String message) {
        super(HttpStatus.CONFLICT, code, message);
    }
}
