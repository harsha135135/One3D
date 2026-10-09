package store.one3d.common;

import org.springframework.http.HttpStatus;

/** A record addressed in the URL does not exist. Returned as 404 Not Found. */
public class NotFoundException extends ApiException {

    public NotFoundException(String code, String message) {
        super(HttpStatus.NOT_FOUND, code, message);
    }
}
