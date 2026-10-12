package store.one3d.common;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.servlet.HandlerMapping;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/** What request handling puts into the log context, and what it keeps out. */
class LogContextTest {

    private static final String UUID_PATTERN = "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}";

    @AfterEach
    void clear() {
        MDC.clear();
    }

    @Test
    void everyRequestGetsAnIdInTheLogsAndTheResponse() throws Exception {
        Map<String, String> seen = handle(new MockHttpServletRequest("GET", "/api/orders"));

        assertThat(seen.get("request_id")).matches(UUID_PATTERN);
        assertThat(seen.get("response header")).isEqualTo(seen.get("request_id"));
        assertThat(MDC.get("request_id")).as("removed after the request").isNull();
    }

    @Test
    void aWellFormedIdFromNginxIsKept() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/orders");
        request.addHeader("X-Request-Id", "3f2a9c41d7e04b6f8a1c2d3e4f5a6b7c");

        assertThat(handle(request).get("request_id")).isEqualTo("3f2a9c41d7e04b6f8a1c2d3e4f5a6b7c");
    }

    @Test
    void anythingElseInTheHeaderIsReplacedSoItCannotWriteIntoTheLogs() throws Exception {
        for (String sent : new String[]{"short", "two words here", "id\nFAKE log line", "x".repeat(65)}) {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/orders");
            request.addHeader("X-Request-Id", sent);

            assertThat(handle(request).get("request_id")).as(sent).matches(UUID_PATTERN);
        }
    }

    @Test
    void idsInThePathAreLoggedAsTheRecordsTheyName() {
        PathIdLogContext context = new PathIdLogContext();
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/ops/attempts/x/qc");
        Map<String, String> variables = new HashMap<>();
        variables.put("attemptId", "0d3d0000-0000-4000-8000-000000000a01");
        variables.put("orderId", "0d3d0000-0000-4000-8000-000000000b01");
        variables.put("requestId", "0d3d0000-0000-4000-8000-000000000c01");
        variables.put("revisionNumber", "2");
        variables.put("jobId", "not-a-uuid\nFAKE");
        request.setAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE, variables);

        context.preHandle(request, new MockHttpServletResponse(), new Object());

        assertThat(MDC.getCopyOfContextMap()).containsOnly(
                Map.entry("attempt_id", "0d3d0000-0000-4000-8000-000000000a01"),
                Map.entry("order_id", "0d3d0000-0000-4000-8000-000000000b01"),
                Map.entry("custom_request_id", "0d3d0000-0000-4000-8000-000000000c01"));

        context.afterCompletion(request, new MockHttpServletResponse(), new Object(), null);
        assertThat(MDC.getCopyOfContextMap()).isNullOrEmpty();
    }

    /** Runs the filter and records the log context as the application saw it. */
    private static Map<String, String> handle(MockHttpServletRequest request) throws Exception {
        AtomicReference<String> during = new AtomicReference<>();
        MockHttpServletResponse response = new MockHttpServletResponse();
        new RequestIdFilter().doFilter(request, response, new MockFilterChain() {
            @Override
            public void doFilter(jakarta.servlet.ServletRequest req, jakarta.servlet.ServletResponse res) {
                during.set(MDC.get("request_id"));
            }
        });
        Map<String, String> seen = new HashMap<>();
        seen.put("request_id", during.get());
        seen.put("response header", response.getHeader("X-Request-Id"));
        return seen;
    }
}
