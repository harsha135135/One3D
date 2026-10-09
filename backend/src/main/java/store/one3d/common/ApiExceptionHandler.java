package store.one3d.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.TypeMismatchException;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.exc.MismatchedInputException;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Converts exceptions into RFC 9457 problem responses
 * ({@code application/problem+json}). Every response has a "code" property
 * that clients can rely on instead of parsing the human-readable detail.
 */
@RestControllerAdvice
class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    ResponseEntity<ProblemDetail> handleApiException(ApiException ex) {
        return ResponseEntity.status(ex.getStatus()).body(problem(ex.getStatus(), ex.getCode(), ex.getMessage()));
    }

    /**
     * A database rule rejected a write. Known race-protection constraints become
     * 409 with a specific code; any other violation is a bug and stays a 500.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ProblemDetail> handleIntegrityViolation(DataIntegrityViolationException ex) {
        return ConstraintConflicts.codeFor(ex)
                .map(code -> ResponseEntity.status(HttpStatus.CONFLICT).body(problem(HttpStatus.CONFLICT, code,
                        "The request conflicts with a change made at the same time. Reload and try again.")))
                .orElseGet(() -> handleUnexpected(ex));
    }

    /** Optimistic version or row-lock conflict: another request changed the same record first. */
    @ExceptionHandler(ConcurrencyFailureException.class)
    ResponseEntity<ProblemDetail> handleConcurrencyFailure(ConcurrencyFailureException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(problem(HttpStatus.CONFLICT, "CONCURRENT_MODIFICATION",
                "The record was changed by another request at the same time. Reload and try again."));
    }

    /** Anything unexpected: log the cause on the server, return no internal details. */
    @ExceptionHandler(Exception.class)
    ResponseEntity<ProblemDetail> handleUnexpected(Exception ex) {
        log.error("Unhandled exception while processing request", ex);
        HttpStatus status = HttpStatus.INTERNAL_SERVER_ERROR;
        return ResponseEntity.status(status).body(problem(status, "INTERNAL_ERROR", "An unexpected error occurred."));
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<FieldViolation> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> new FieldViolation(error.getField(), error.getDefaultMessage()))
                .toList();
        ProblemDetail body = problem(status, "VALIDATION_FAILED", "One or more fields are invalid.");
        body.setProperty("errors", errors);
        return handleExceptionInternal(ex, body, headers, status, request);
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(
            HttpMessageNotReadableException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        ProblemDetail body = invalidFieldValue(ex).orElseGet(() ->
                problem(status, "MALFORMED_REQUEST", "The request body is missing or is not valid JSON."));
        return handleExceptionInternal(ex, body, headers, status, request);
    }

    /**
     * Well-formed JSON with a value of the wrong type for a field, such as 1.5
     * for an integer or an unknown enum name, reported as a field error.
     */
    private static Optional<ProblemDetail> invalidFieldValue(HttpMessageNotReadableException ex) {
        if (!(ex.getCause() instanceof MismatchedInputException mismatch) || mismatch.getPath().isEmpty()) {
            return Optional.empty();
        }
        String field = mismatch.getPath().stream()
                .map(ApiExceptionHandler::pathSegment)
                .collect(Collectors.joining("."));
        ProblemDetail body = problem(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "One or more fields are invalid.");
        body.setProperty("errors", List.of(new FieldViolation(field, "has a value of the wrong type")));
        return Optional.of(body);
    }

    private static String pathSegment(JacksonException.Reference reference) {
        return reference.getPropertyName() != null ? reference.getPropertyName() : "[" + reference.getIndex() + "]";
    }

    @Override
    protected ResponseEntity<Object> handleTypeMismatch(
            TypeMismatchException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        String detail = "Parameter '%s' has an invalid value.".formatted(ex.getPropertyName());
        return handleExceptionInternal(ex, problem(status, "INVALID_PARAMETER", detail), headers, status, request);
    }

    /** Adds a code to Spring's own problem responses too, for example 405 Method Not Allowed. */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception ex, Object body, HttpHeaders headers, HttpStatusCode statusCode, WebRequest request) {
        ResponseEntity<Object> response = super.handleExceptionInternal(ex, body, headers, statusCode, request);
        if (response != null && response.getBody() instanceof ProblemDetail problem && !hasCode(problem)) {
            HttpStatus status = HttpStatus.resolve(statusCode.value());
            problem.setProperty("code", status != null ? status.name() : "HTTP_" + statusCode.value());
        }
        return response;
    }

    private static boolean hasCode(ProblemDetail problem) {
        return problem.getProperties() != null && problem.getProperties().containsKey("code");
    }

    private static ProblemDetail problem(HttpStatusCode status, String code, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setProperty("code", code);
        return problem;
    }
}
