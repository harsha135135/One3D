package store.one3d.identity;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** Who the signed-in user is, for the frontend. */
@RestController
class MeController {

    /** {@code paymentMode}: RAZORPAY_TEST (hosted test-mode checkout), DEMO (shortcut, no payment) or NONE. */
    record MeResponse(UUID id, String email, List<String> roles, boolean demoPaymentEnabled, String paymentMode) {
    }

    private final CurrentUser currentUser;
    private final boolean demoPaymentEnabled;
    private final String paymentMode;

    MeController(CurrentUser currentUser,
                 @Value("${one3d.demo.simulated-payment-enabled}") boolean demoPaymentEnabled,
                 @Value("${one3d.payments.razorpay.enabled}") boolean razorpayEnabled) {
        this.currentUser = currentUser;
        this.demoPaymentEnabled = demoPaymentEnabled;
        this.paymentMode = razorpayEnabled ? "RAZORPAY_TEST" : demoPaymentEnabled ? "DEMO" : "NONE";
    }

    @GetMapping("/api/me")
    MeResponse me() {
        CurrentUser.Identity user = currentUser.require();
        List<String> roles = user.roles().stream().map(role -> role.substring("ROLE_".length())).toList();
        return new MeResponse(user.id(), user.email(), roles, demoPaymentEnabled, paymentMode);
    }
}
