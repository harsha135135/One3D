package store.one3d.payments;

import java.time.Instant;
import java.util.UUID;

/** A recorded payment as operators see it. */
public record PaymentView(
        UUID id,
        String providerPaymentId,
        String providerOrderId,
        UUID orderId,
        long amountMinor,
        String currency,
        Instant providerCreatedAt,
        Instant recordedAt,
        String source,
        String outcome,
        String reviewReason,
        Instant resolvedAt,
        String resolutionNote) {
}
