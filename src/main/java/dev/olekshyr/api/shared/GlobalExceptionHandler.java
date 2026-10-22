package dev.olekshyr.api.shared;

import io.opentelemetry.api.trace.Span;
import java.time.Clock;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Maps every exception to the {@link ApiErrorResponse} envelope. Spring MVC's
 * own exceptions keep the status Spring picked for them (D20).
 */
@Slf4j
@RestControllerAdvice
@RequiredArgsConstructor
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

  private static final String ALL_ZEROS_TRACE_ID = "0".repeat(32);

  private final MessageSource messageSource;
  private final Clock clock;

  @ExceptionHandler(ApiException.class)
  ResponseEntity<Object> handleApiException(
    ApiException ex,
    WebRequest request
  ) {
    List<ApiFieldError> details = ex
      .violations()
      .stream()
      .map(v -> new ApiFieldError(v.field(), v.code().name(), message(v.code()))
      )
      .toList();
    return ResponseEntity
      .status(ex.status())
      .body(
        buildResponse(
          ex.errorCode(),
          request,
          details.isEmpty() ? null : details
        )
      );
  }

  /**
   * Fallback for unique-constraint races the services' pre-checks miss (a
   * duplicate slug committed in between) and constraints without a dedicated
   * code, such as a duplicate tag name in a locale.
   */
  @ExceptionHandler(DataIntegrityViolationException.class)
  ResponseEntity<Object> handleDataIntegrityViolation(
    DataIntegrityViolationException ex,
    WebRequest request
  ) {
    log.warn(
      "Constraint violation for path {}: {}",
      path(request),
      ex.getMostSpecificCause().getMessage()
    );
    return ResponseEntity
      .status(HttpStatus.CONFLICT)
      .body(buildResponse(ErrorCode.CONFLICT, request, null));
  }

  @ExceptionHandler(Exception.class)
  ResponseEntity<Object> handleGeneric(Exception ex, WebRequest request) {
    log.error(
      "Unhandled exception for path {}: {}",
      path(request),
      ex.getMessage(),
      ex
    );
    return ResponseEntity
      .internalServerError()
      .body(buildResponse(ErrorCode.INTERNAL_ERROR, request, null));
  }

  @Override
  protected ResponseEntity<Object> handleMethodArgumentNotValid(
    MethodArgumentNotValidException ex,
    HttpHeaders headers,
    HttpStatusCode status,
    WebRequest request
  ) {
    List<ApiFieldError> details = ex
      .getBindingResult()
      .getFieldErrors()
      .stream()
      .map(fe ->
        new ApiFieldError(
          fe.getField(),
          toConstraintCode(fe.getCode()),
          fe.getDefaultMessage()
        )
      )
      .toList();

    return validationFailed(ex, details, headers, status, request);
  }

  @Override
  protected ResponseEntity<Object> handleHandlerMethodValidationException(
    HandlerMethodValidationException ex,
    HttpHeaders headers,
    HttpStatusCode status,
    WebRequest request
  ) {
    List<ApiFieldError> details = ex
      .getParameterValidationResults()
      .stream()
      .flatMap(pvr ->
        pvr
          .getResolvableErrors()
          .stream()
          .map(err -> {
            String[] codes = err.getCodes();
            String code = codes != null && codes.length > 0 ? codes[0] : null;
            return new ApiFieldError(
              pvr.getMethodParameter().getParameterName(),
              toConstraintCode(code),
              err.getDefaultMessage()
            );
          })
      )
      .toList();

    return validationFailed(ex, details, headers, status, request);
  }

  /** Every other Spring MVC exception lands here with Spring's own status. */
  @Override
  protected ResponseEntity<Object> handleExceptionInternal(
    Exception ex,
    @Nullable Object body,
    HttpHeaders headers,
    HttpStatusCode statusCode,
    WebRequest request
  ) {
    if (statusCode.is5xxServerError()) {
      log.error(
        "Request failed for path {}: {}",
        path(request),
        ex.getMessage(),
        ex
      );
    }
    Object envelope = body instanceof ApiErrorResponse
      ? body
      : buildResponse(errorCodeFor(statusCode), request, null);
    return super.handleExceptionInternal(
      ex,
      envelope,
      headers,
      statusCode,
      request
    );
  }

  private ResponseEntity<Object> validationFailed(
    Exception ex,
    List<ApiFieldError> details,
    HttpHeaders headers,
    HttpStatusCode status,
    WebRequest request
  ) {
    return handleExceptionInternal(
      ex,
      buildResponse(ErrorCode.VALIDATION_FAILED, request, details),
      headers,
      status,
      request
    );
  }

  private ApiErrorResponse buildResponse(
    ErrorCode errorCode,
    WebRequest request,
    @Nullable List<ApiFieldError> details
  ) {
    String message = message(errorCode.messageKey());

    String spanTraceId = Span.current().getSpanContext().getTraceId();
    String traceId = ALL_ZEROS_TRACE_ID.equals(spanTraceId)
      ? null
      : spanTraceId;

    return new ApiErrorResponse(
      errorCode.name(),
      message,
      clock.instant(),
      path(request),
      traceId,
      details
    );
  }

  private String message(FieldErrorCode code) {
    return message(code.messageKey());
  }

  private String message(String key) {
    return messageSource.getMessage(key, null, LocaleContextHolder.getLocale());
  }

  /**
   * Codes for statuses Spring picked (D20). Statuses {@link HttpStatus} doesn't
   * define, like 499, map by series instead of failing.
   */
  private static ErrorCode errorCodeFor(HttpStatusCode status) {
    if (!status.is4xxClientError()) return ErrorCode.INTERNAL_ERROR;
    HttpStatus known = HttpStatus.resolve(status.value());
    if (known == null) return ErrorCode.REQUEST_REJECTED;
    return switch (known) {
      case BAD_REQUEST -> ErrorCode.VALIDATION_FAILED;
      case NOT_FOUND -> ErrorCode.NOT_FOUND;
      case METHOD_NOT_ALLOWED,
        NOT_ACCEPTABLE,
        UNSUPPORTED_MEDIA_TYPE -> ErrorCode.UNSUPPORTED_REQUEST;
      case CONFLICT -> ErrorCode.CONFLICT;
      default -> ErrorCode.REQUEST_REJECTED;
    };
  }

  private static String path(WebRequest request) {
    return request instanceof ServletWebRequest servletRequest
      ? servletRequest.getRequest().getRequestURI()
      : null;
  }

  private static String toConstraintCode(String constraintCode) {
    if (constraintCode == null) return null;
    return constraintCode.replaceAll("([a-z])([A-Z])", "$1_$2").toUpperCase();
  }
}
