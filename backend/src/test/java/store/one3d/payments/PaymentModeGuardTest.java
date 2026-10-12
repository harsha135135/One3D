package store.one3d.payments;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/** Startup refuses unsafe payment setups (no database or web layer needed). */
class PaymentModeGuardTest {

    private final ApplicationContextRunner context = new ApplicationContextRunner()
            .withUserConfiguration(PaymentModeGuard.class);

    private static final String[] TEST_KEYS = {
            "one3d.payments.razorpay.enabled=true",
            "one3d.payments.razorpay.key-id=rzp_test_abc",
            "one3d.payments.razorpay.key-secret=secret",
            "one3d.payments.razorpay.webhook-secret=webhook-secret"};

    @Test
    void razorpayWithTestKeysStarts() {
        context.withPropertyValues(TEST_KEYS).run(started -> assertThat(started).hasNotFailed());
    }

    @Test
    void theDemoShortcutAloneStartsLocally() {
        context.withPropertyValues("one3d.demo.simulated-payment-enabled=true")
                .run(started -> assertThat(started).hasNotFailed());
    }

    @Test
    void razorpayWithoutAllKeysDoesNotStart() {
        context.withPropertyValues("one3d.payments.razorpay.enabled=true", "one3d.payments.razorpay.key-id=rzp_test_abc")
                .run(failed -> assertThat(failed).getFailure().hasStackTraceContaining("are not all set"));
    }

    @Test
    void aLiveKeyDoesNotStart() {
        context.withPropertyValues(TEST_KEYS).withPropertyValues("one3d.payments.razorpay.key-id=rzp_live_abc")
                .run(failed -> assertThat(failed).getFailure().hasStackTraceContaining("test-mode keys"));
    }

    @Test
    void theDemoShortcutTogetherWithRazorpayDoesNotStart() {
        context.withPropertyValues(TEST_KEYS).withPropertyValues("one3d.demo.simulated-payment-enabled=true")
                .run(failed -> assertThat(failed).getFailure().hasStackTraceContaining("together with Razorpay"));
    }

    @Test
    void theDemoShortcutUnderTheAwsProfileDoesNotStart() {
        context.withPropertyValues("one3d.demo.simulated-payment-enabled=true")
                .withInitializer(started -> started.getEnvironment().setActiveProfiles("aws"))
                .run(failed -> assertThat(failed).getFailure().hasStackTraceContaining("aws profile"));
    }
}
