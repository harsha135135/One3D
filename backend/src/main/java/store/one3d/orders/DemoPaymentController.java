package store.one3d.orders;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * DEMO ONLY: confirms an order without any payment so the production workflow
 * can be shown before payments exist. The endpoint exists only when
 * {@code one3d.demo.simulated-payment-enabled=true} (the "demo" profile), and
 * the order records {@link ConfirmationSource#DEMO_SIMULATED}. Checkpoint 6
 * replaces it with verified test-mode payments.
 */
@RestController
@ConditionalOnProperty(name = "one3d.demo.simulated-payment-enabled", havingValue = "true")
@RequestMapping("/api/demo/orders")
class DemoPaymentController {

    private final OrderService orderService;

    DemoPaymentController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping("/{orderId}/simulate-payment")
    OrderResponse simulatePayment(@PathVariable UUID orderId) {
        return orderService.confirm(orderId, ConfirmationSource.DEMO_SIMULATED);
    }
}
