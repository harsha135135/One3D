package store.one3d.orders;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import store.one3d.common.PageResponse;
import store.one3d.identity.CurrentUser;

import java.net.URI;
import java.util.UUID;

/**
 * The signed-in customer's own orders. HTTP mapping only: validation happens on
 * the request record, rules and ownership checks in OrderService.
 */
@RestController
@RequestMapping("/api/orders")
class OrderController {

    private final OrderService orderService;
    private final CurrentUser currentUser;

    OrderController(OrderService orderService, CurrentUser currentUser) {
        this.orderService = orderService;
        this.currentUser = currentUser;
    }

    /** The customer's orders, newest first. {@code size} is at most 50. */
    @GetMapping
    PageResponse<OrderResponse> listOrders(@RequestParam(defaultValue = "0") int page,
                                           @RequestParam(defaultValue = "20") int size) {
        return orderService.listOwnOrders(currentUser.id(), page, size);
    }

    @PostMapping
    ResponseEntity<OrderResponse> placeOrder(@Valid @RequestBody PlaceOrderRequest request) {
        OrderResponse order = orderService.placeOrder(currentUser.id(), request.productConfigurationId(), request.quantity());
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(order.id())
                .toUri();
        return ResponseEntity.created(location).body(order);
    }

    @GetMapping("/{orderId}")
    OrderResponse getOrder(@PathVariable UUID orderId) {
        return orderService.getOwnOrder(currentUser.id(), orderId);
    }
}
