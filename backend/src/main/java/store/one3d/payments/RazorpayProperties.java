package store.one3d.payments;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/** Settings under {@code one3d.payments.razorpay}; see application.yaml. Never logged. */
@ConfigurationProperties("one3d.payments.razorpay")
record RazorpayProperties(
        boolean enabled,
        String keyId,
        String keySecret,
        String webhookSecret,
        String baseUrl,
        Duration connectTimeout,
        Duration requestTimeout,
        Duration checkoutWindow) {

    /** Razorpay issues test-mode key ids with this prefix; live ids start with rzp_live_. */
    static final String TEST_KEY_PREFIX = "rzp_test_";

    @Override
    public String toString() {
        return "RazorpayProperties[enabled=" + enabled + ", keyId=" + (keyId == null ? null : TEST_KEY_PREFIX + "…")
                + ", secrets hidden]";
    }
}
