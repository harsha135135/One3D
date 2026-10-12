package store.one3d.orders;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import store.one3d.identity.CurrentUser;

import java.util.UUID;

/**
 * DEMO ONLY: confirms an order without any payment, for local demonstrations
 * without payment keys. The endpoint exists only when
 * {@code one3d.demo.simulated-payment-enabled=true} (the "demo" profile); the
 * order records {@link ConfirmationSource#DEMO_SIMULATED} and its timeline says
 * no payment was taken. Startup fails if it is enabled together with Razorpay
 * or under the aws profile ({@code payments.PaymentModeGuard}).
 */
@RestController
@ConditionalOnProperty(name = "one3d.demo.simulated-payment-enabled", havingValue = "true")
@RequestMapping("/api/demo/orders")
class DemoPaymentController {

    private final OrderService orderService;
    private final CurrentUser currentUser;

    DemoPaymentController(OrderService orderService, CurrentUser currentUser) {
        this.orderService = orderService;
        this.currentUser = currentUser;
    }

    /** Only for the caller's own order. */
    @PostMapping("/{orderId}/simulate-payment")
    OrderResponse simulatePayment(@PathVariable UUID orderId) {
        return orderService.confirmDemoPayment(currentUser.id(), orderId);
    }
}
