package store.one3d.common;

import org.springframework.http.HttpStatus;

/**
 * An expected business error with the HTTP status and stable machine-readable
 * code the API returns for it. Unexpected failures should not use this class.
 */
public abstract class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    protected ApiException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }
}
