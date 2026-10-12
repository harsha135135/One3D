package store.one3d.common;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.Map;
import java.util.regex.Pattern;

/**
 * Puts the ids named in the request path (order, job, attempt, file and so on)
 * into the log context while the request is handled, so every log line it
 * writes says which record it was about. Only UUID-shaped values are copied;
 * query strings, headers and personal data never are.
 */
@Configuration
class PathIdLogContext implements WebMvcConfigurer, HandlerInterceptor {

    /** Path variable name to log field. {@code requestId} is a custom-part request, not the HTTP request. */
    static final Map<String, String> FIELDS = Map.of(
            "orderId", "order_id",
            "jobId", "job_id",
            "attemptId", "attempt_id",
            "artifactId", "artifact_id",
            "printerId", "printer_id",
            "requestId", "custom_request_id",
            "quoteId", "quote_id",
            "paymentId", "payment_id");

    private static final Pattern UUID_SHAPE =
            Pattern.compile("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(this).addPathPatterns("/api/**");
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE) instanceof Map<?, ?> variables) {
            variables.forEach((name, value) -> {
                String field = FIELDS.get(String.valueOf(name));
                if (field != null && value != null && UUID_SHAPE.matcher(value.toString()).matches()) {
                    MDC.put(field, value.toString());
                }
            });
        }
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler,
                                Exception ex) {
        FIELDS.values().forEach(MDC::remove);
    }
}
