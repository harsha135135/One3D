package store.one3d.payments;

import org.springframework.http.HttpStatus;
import store.one3d.common.ApiException;

/** Razorpay could not be reached or answered unexpectedly; nothing was decided. Returned as 503. */
class ProviderUnavailableException extends ApiException {

    ProviderUnavailableException() {
        super(HttpStatus.SERVICE_UNAVAILABLE, "PAYMENT_PROVIDER_UNAVAILABLE",
                "The payment provider could not be reached. Try again.");
    }
}
