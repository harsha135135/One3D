package store.one3d.support;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Full application context against real PostgreSQL. Every integration test
 * uses the same annotation, so Spring caches one context and one container
 * for the whole run instead of starting a new database per class.
 * Payments are enabled in Razorpay test mode against an in-memory provider
 * ({@link FakeRazorpay}), so tests confirm orders through checkout and a
 * signed webhook; the demo shortcut is off. The printer simulator and storage
 * run as separate containers (see {@link TestcontainersConfiguration}).
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@SpringBootTest(properties = {
        // Tests drive the dispatch worker and observer explicitly (runOnce /
        // observeOnce) so no background thread picks up another test's rows.
        "one3d.worker.enabled=false",
        "one3d.worker.retry-backoff=0s",
        "one3d.simulator.request-timeout=2s",
        // Work-queue gauges read the table on every scrape instead of every 5 s.
        "one3d.metrics.work-refresh=0s",
        // Orders are paid the real way: a test-mode checkout (FakeRazorpay)
        // and a signed webhook. These are dummy values, never real keys.
        "one3d.payments.razorpay.enabled=true",
        "one3d.payments.razorpay.key-id=rzp_test_integration",
        "one3d.payments.razorpay.key-secret=test-key-secret",
        "one3d.payments.razorpay.webhook-secret=" + RazorpayEvents.TEST_WEBHOOK_SECRET})
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, SignedInByDefault.class})
public @interface PostgresIntegrationTest {
}
