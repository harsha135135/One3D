package store.one3d.orders;

import org.springframework.http.HttpStatus;
import store.one3d.common.ApiException;

import java.util.UUID;

/** The request is well formed, but the configuration does not exist or has no approved recipe. */
class ConfigurationNotOrderableException extends ApiException {

    ConfigurationNotOrderableException(UUID configurationId) {
        super(HttpStatus.UNPROCESSABLE_CONTENT, "CONFIGURATION_NOT_ORDERABLE",
                "Product configuration " + configurationId + " does not exist or cannot be ordered.");
    }
}
