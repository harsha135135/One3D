package store.one3d.payments;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import store.one3d.identity.CurrentUser;

import java.util.UUID;

/** Test-mode payment for the signed-in customer's own order. */
@RestController
@RequestMapping("/api/orders/{orderId}")
class CheckoutController {

    private final CheckoutService checkouts;
    private final CurrentUser currentUser;

    CheckoutController(CheckoutService checkouts, CurrentUser currentUser) {
        this.checkouts = checkouts;
        this.currentUser = currentUser;
    }

    /** Opens (or reuses) a Razorpay test-mode checkout for the order total. */
    @PostMapping("/checkout")
    CheckoutResponse open(@PathVariable UUID orderId) {
        return checkouts.open(currentUser.id(), orderId);
    }

    /**
     * Called by the browser when Checkout reports success. The browser's report
     * is not evidence: the backend asks Razorpay itself.
     */
    @PostMapping("/payment-check")
    PaymentCheckResponse check(@PathVariable UUID orderId) {
        return checkouts.check(currentUser.id(), orderId);
    }
}
