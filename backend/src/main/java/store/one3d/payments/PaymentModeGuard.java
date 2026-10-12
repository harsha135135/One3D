package store.one3d.payments;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;

/**
 * Refuses to start with an unsafe payment setup:
 * <ul>
 *   <li>Razorpay enabled without all three keys, or with a key that is not a
 *       test-mode key (V1 never takes real money);</li>
 *   <li>the demo shortcut (confirms orders without payment) together with
 *       Razorpay, so it can never bypass the hosted checkout;</li>
 *   <li>the demo shortcut under the {@code aws} profile (the deployed demo).</li>
 * </ul>
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(RazorpayProperties.class)
class PaymentModeGuard {

    PaymentModeGuard(RazorpayProperties razorpay, Environment environment,
                     @Value("${one3d.demo.simulated-payment-enabled:false}") boolean demoShortcut) {
        if (razorpay.enabled()) {
            if (blank(razorpay.keyId()) || blank(razorpay.keySecret()) || blank(razorpay.webhookSecret())) {
                throw new IllegalStateException("Razorpay is enabled but RAZORPAY_KEY_ID, RAZORPAY_KEY_SECRET "
                        + "and RAZORPAY_WEBHOOK_SECRET are not all set");
            }
            if (!razorpay.keyId().startsWith(RazorpayProperties.TEST_KEY_PREFIX)) {
                throw new IllegalStateException("Only Razorpay test-mode keys (rzp_test_…) are allowed in V1");
            }
            if (demoShortcut) {
                throw new IllegalStateException("The demo payment shortcut cannot be enabled together with Razorpay");
            }
        }
        if (demoShortcut && environment.acceptsProfiles(Profiles.of("aws"))) {
            throw new IllegalStateException("The demo payment shortcut cannot be enabled under the aws profile");
        }
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
