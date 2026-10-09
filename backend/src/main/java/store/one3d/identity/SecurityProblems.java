package store.one3d.identity;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Problem responses for errors raised by the security filters, which run
 * before any controller and so never reach {@code ApiExceptionHandler}.
 */
@Component
class SecurityProblems implements AuthenticationEntryPoint, AccessDeniedHandler {

    private final JsonMapper json;

    SecurityProblems(JsonMapper json) {
        this.json = json;
    }

    /** No valid session: 401. API calls get JSON instead of a redirect to the login page. */
    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException exception) throws IOException {
        write(request, response, HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "Sign in to use this endpoint.");
    }

    /** Signed in but not allowed (wrong role), or a missing or invalid CSRF token: 403. */
    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException exception) throws IOException {
        if (exception instanceof CsrfException) {
            write(request, response, HttpStatus.FORBIDDEN, "CSRF_TOKEN_INVALID",
                    "The request is missing a valid CSRF token (X-XSRF-TOKEN header).");
        } else {
            write(request, response, HttpStatus.FORBIDDEN, "ACCESS_DENIED", "Your role does not allow this request.");
        }
    }

    private void write(HttpServletRequest request, HttpServletResponse response, HttpStatus status,
                       String code, String detail) throws IOException {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("type", "about:blank");
        body.put("title", status.getReasonPhrase());
        body.put("status", status.value());
        body.put("detail", detail);
        body.put("instance", request.getRequestURI());
        body.put("code", code);
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.getWriter().write(json.writeValueAsString(body));
    }
}
