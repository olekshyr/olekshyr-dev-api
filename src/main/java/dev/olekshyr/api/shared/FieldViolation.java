package dev.olekshyr.api.shared;

/** One field an {@link ApiException} rejects; the handler localizes it. */
public record FieldViolation(String field, FieldErrorCode code) {}
