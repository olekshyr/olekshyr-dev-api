package dev.olekshyr.api.shared;

import org.springframework.http.HttpStatus;

/**
 * Base for domain exceptions that map to an error response. Modules extend it
 * so the handler here never depends on them (D21).
 */
public abstract class ApiException extends RuntimeException {

  private final ErrorCode errorCode;
  private final HttpStatus status;

  protected ApiException(
    ErrorCode errorCode,
    HttpStatus status,
    String message
  ) {
    super(message);
    this.errorCode = errorCode;
    this.status = status;
  }

  public ErrorCode errorCode() {
    return errorCode;
  }

  public HttpStatus status() {
    return status;
  }
}
