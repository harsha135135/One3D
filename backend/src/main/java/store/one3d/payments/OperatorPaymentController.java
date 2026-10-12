package store.one3d.payments;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** Payments for operators, mainly those that need a manual check. */
@RestController
@RequestMapping("/api/ops/payments")
class OperatorPaymentController {

    record ResolveRequest(@NotBlank @Size(max = 1000) String note) {
    }

    private final PaymentReviews reviews;

    OperatorPaymentController(PaymentReviews reviews) {
        this.reviews = reviews;
    }

    /** {@code reviews=true} (default): unresolved payments that need a manual check; otherwise the latest payments. */
    @GetMapping
    List<PaymentView> list(@RequestParam(defaultValue = "true") boolean reviews) {
        return this.reviews.list(reviews);
    }

    /** Records how an operator settled a payment that needed a check. */
    @PostMapping("/{paymentId}/resolve")
    List<PaymentView> resolve(@PathVariable UUID paymentId, @Valid @RequestBody ResolveRequest body) {
        return reviews.resolve(paymentId, body.note());
    }
}
