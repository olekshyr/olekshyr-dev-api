package dev.olekshyr.api.shared;

import java.util.List;
import org.springframework.http.HttpStatus;

/**
 * Base for domain exceptions that map to an error response. Modules extend it
 * so the handler here never depends on them (D21).
 */
public abstract class ApiException extends RuntimeException {

  private final ErrorCode errorCode;
  private final HttpStatus status;
  private final transient List<FieldViolation> violations;

  protected ApiException(
    ErrorCode errorCode,
    HttpStatus status,
    String message
  ) {
    this(errorCode, status, message, List.of());
  }

  /** The violations become the response's per-field {@code details}. */
  protected ApiException(
    ErrorCode errorCode,
    HttpStatus status,
    String message,
    List<FieldViolation> violations
  ) {
    super(message);
    this.errorCode = errorCode;
    this.status = status;
    this.violations = List.copyOf(violations);
  }

  public ErrorCode errorCode() {
    return errorCode;
  }

  public HttpStatus status() {
    return status;
  }

  public List<FieldViolation> violations() {
    return violations;
  }
}
