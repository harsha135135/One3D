package store.one3d.files;

import org.springframework.http.HttpStatus;
import store.one3d.common.ApiException;

/** Storage is unreachable or misconfigured; nothing was changed. Returned as 503. */
class StorageProblemException extends ApiException {

    StorageProblemException(String code, String message) {
        super(HttpStatus.SERVICE_UNAVAILABLE, code, message);
    }
}
