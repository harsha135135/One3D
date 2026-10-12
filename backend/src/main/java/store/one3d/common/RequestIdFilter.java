package store.one3d.common;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Gives every request an id, in the logs ({@code request_id}) and in the
 * {@code X-Request-Id} response header, so a report from a browser can be
 * matched to the log lines. Nginx sends its own id in that header; anything
 * that is not a short token of letters, digits and dashes is replaced, so a
 * client cannot write arbitrary text into the logs.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Request-Id";
    public static final String MDC_KEY = "request_id";

    private static final Pattern ACCEPTED = Pattern.compile("[A-Za-z0-9-]{8,64}");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String inbound = request.getHeader(HEADER);
        String id = inbound != null && ACCEPTED.matcher(inbound).matches() ? inbound : UUID.randomUUID().toString();
        response.setHeader(HEADER, id);
        try (MDC.MDCCloseable ignored = MDC.putCloseable(MDC_KEY, id)) {
            chain.doFilter(request, response);
        }
    }
}
