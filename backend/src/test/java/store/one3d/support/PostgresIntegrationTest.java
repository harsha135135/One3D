package store.one3d.support;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Full application context against real PostgreSQL. Every integration test
 * uses the same annotation, so Spring caches one context and one container
 * for the whole run instead of starting a new database per class.
 * The "demo" profile enables the simulated-payment shortcut that the
 * production tests use to confirm orders. The printer simulator runs as a
 * separate container (see {@link TestcontainersConfiguration}).
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@SpringBootTest(properties = {
        // Tests drive the dispatch worker and observer explicitly (runOnce /
        // observeOnce) so no background thread picks up another test's rows.
        "one3d.worker.enabled=false",
        "one3d.worker.retry-backoff=0s",
        "one3d.simulator.request-timeout=2s"})
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("demo")
public @interface PostgresIntegrationTest {
}
