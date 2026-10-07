package store.one3d.orders;

import org.springframework.http.HttpStatus;
import store.one3d.common.ApiException;

import java.util.UUID;

class OrderNotFoundException extends ApiException {

    OrderNotFoundException(UUID orderId) {
        super(HttpStatus.NOT_FOUND, "ORDER_NOT_FOUND", "Order " + orderId + " was not found.");
    }
}
