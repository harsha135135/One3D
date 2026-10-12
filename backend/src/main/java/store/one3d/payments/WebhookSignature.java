package store.one3d.payments;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.HexFormat;

/**
 * Razorpay's webhook signature: HMAC-SHA256 of the exact request body bytes,
 * keyed with the webhook secret, sent as lower-case hex in
 * {@code X-Razorpay-Signature}. The body must be the raw bytes received, not
 * re-serialized JSON.
 */
final class WebhookSignature {

    private WebhookSignature() {
    }

    static String sign(byte[] body, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(body));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA256 is always available in the JDK", e);
        }
    }

    /** Constant-time comparison, so the check does not reveal how much of a forged signature matched. */
    static boolean matches(byte[] body, String signature, String secret) {
        if (signature == null || signature.isBlank()) {
            return false;
        }
        byte[] expected = sign(body, secret).getBytes(StandardCharsets.US_ASCII);
        byte[] given = signature.trim().toLowerCase(java.util.Locale.ROOT).getBytes(StandardCharsets.US_ASCII);
        return MessageDigest.isEqual(expected, given);
    }
}
