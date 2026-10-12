package store.one3d.payments;

import java.time.Instant;
import java.util.UUID;

/**
 * What the browser needs to open Razorpay Checkout in test mode. The key id is
 * public by design; the secret never leaves the server.
 */
public record CheckoutResponse(
        UUID checkoutId,
        UUID orderId,
        String keyId,
        String providerOrderId,
        long amountMinor,
        String currency,
        Instant expiresAt,
        String itemName,
        boolean testMode) {
}
