package store.one3d.common;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalManagementPort;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import store.one3d.support.RazorpayEvents;
import store.one3d.support.TestcontainersConfiguration;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Health and metrics are served on the separate management port only, and
 * nothing else from Actuator is exposed there (anything else is refused or not
 * found). The API port serves neither.
 * Runs real HTTP servers on random ports (its own application context).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "management.server.port=0",
        "one3d.worker.enabled=false",
        "one3d.payments.razorpay.enabled=true",
        "one3d.payments.razorpay.key-id=rzp_test_integration",
        "one3d.payments.razorpay.key-secret=test-key-secret",
        "one3d.payments.razorpay.webhook-secret=" + RazorpayEvents.TEST_WEBHOOK_SECRET})
@Import(TestcontainersConfiguration.class)
class ManagementPortIT {

    private final HttpClient http = HttpClient.newHttpClient();

    @LocalServerPort
    int apiPort;

    @LocalManagementPort
    int managementPort;

    @Test
    void healthAndMetricsAreOnTheManagementPort() throws Exception {
        assertThat(managementPort).isNotEqualTo(apiPort);

        HttpResponse<String> health = get(managementPort, "/actuator/health");
        assertThat(health.statusCode()).isEqualTo(200);
        assertThat(health.body()).contains("\"status\":\"UP\"").doesNotContain("components").doesNotContain("details");

        HttpResponse<String> metrics = get(managementPort, "/actuator/prometheus");
        assertThat(metrics.statusCode()).isEqualTo(200);
        assertThat(metrics.body())
                .contains("one3d_work_items{kind=\"DISPATCH_START\",status=\"PENDING\"}")
                .contains("one3d_work_oldest_age_seconds{kind=\"VERIFY_ARTIFACT\"}")
                .contains("one3d_dispatch_results_total{result=\"unknown\"}")
                .contains("one3d_attempts_awaiting_reconciliation");
    }

    @Test
    void onlyHealthAndMetricsAreExposed() throws Exception {
        for (String endpoint : new String[]{"/actuator", "/actuator/env", "/actuator/beans", "/actuator/heapdump",
                "/actuator/loggers", "/actuator/metrics", "/actuator/configprops"}) {
            assertThat(get(managementPort, endpoint).statusCode()).as(endpoint).isIn(401, 403, 404);
        }
    }

    @Test
    void theApiPortServesNoActuatorEndpoints() throws Exception {
        for (String endpoint : new String[]{"/actuator/health", "/actuator/prometheus", "/actuator/env"}) {
            HttpResponse<String> response = get(apiPort, endpoint);
            assertThat(response.statusCode()).as(endpoint).isIn(401, 403, 404);
            assertThat(response.body()).as(endpoint).doesNotContain("one3d_").doesNotContain("\"UP\"");
        }
    }

    private HttpResponse<String> get(int port, String path) throws Exception {
        return http.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
    }
}
