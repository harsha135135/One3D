package store.one3d.common;

/** One invalid request field, returned in the "errors" list of a 400 response. */
public record FieldViolation(String field, String message) {
}
