package store.one3d.files;

import org.springframework.http.HttpStatus;
import store.one3d.common.ApiException;

/** The requested upload breaks a file rule (name, type or size). Returned as 400. */
class InvalidUploadException extends ApiException {

    InvalidUploadException(String code, String message) {
        super(HttpStatus.BAD_REQUEST, code, message);
    }
}
