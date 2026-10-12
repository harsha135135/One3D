package store.one3d.payments;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import store.one3d.common.ConflictException;
import store.one3d.common.Timestamps;

import java.util.List;
import java.util.UUID;

/**
 * Payments that need a manual check. Resolving one records what the operator
 * decided; no money moves in V1 (refunds happen in the Razorpay dashboard).
 */
@Service
class PaymentReviews {

    private static final int LIMIT = 100;

    private final PaymentStore payments;

    PaymentReviews(PaymentStore payments) {
        this.payments = payments;
    }

    @Transactional(readOnly = true)
    public List<PaymentView> list(boolean unresolvedReviewsOnly) {
        return payments.list(unresolvedReviewsOnly, LIMIT);
    }

    @Transactional
    public List<PaymentView> resolve(UUID paymentId, String note) {
        if (!payments.resolve(paymentId, note, Timestamps.now())) {
            throw new ConflictException("NOTHING_TO_RESOLVE", "Payment " + paymentId + " has no open review.");
        }
        return payments.list(true, LIMIT);
    }
}
