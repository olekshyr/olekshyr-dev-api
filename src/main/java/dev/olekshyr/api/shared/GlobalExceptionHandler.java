package dev.olekshyr.api.shared;

import io.opentelemetry.api.trace.Span;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@Slf4j
@RestControllerAdvice
@RequiredArgsConstructor
public class GlobalExceptionHandler {

  private static final String ALL_ZEROS_TRACE_ID = "0".repeat(32);

  private final MessageSource messageSource;

  @ExceptionHandler(MethodArgumentNotValidException.class)
  ResponseEntity<ApiErrorResponse> handleMethodArgumentNotValid(
    MethodArgumentNotValidException ex,
    HttpServletRequest request
  ) {
    List<ApiFieldError> details = ex
      .getBindingResult()
      .getFieldErrors()
      .stream()
      .map(fe ->
        new ApiFieldError(
          fe.getField(),
          toErrorCode(fe.getCode()),
          fe.getDefaultMessage()
        )
      )
      .toList();

    return ResponseEntity
      .badRequest()
      .body(buildResponse(ErrorCode.VALIDATION_FAILED, request, details));
  }

  @ExceptionHandler(HandlerMethodValidationException.class)
  ResponseEntity<ApiErrorResponse> handleHandlerMethodValidation(
    HandlerMethodValidationException ex,
    HttpServletRequest request
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
              toErrorCode(code),
              err.getDefaultMessage()
            );
          })
      )
      .toList();

    return ResponseEntity
      .badRequest()
      .body(buildResponse(ErrorCode.VALIDATION_FAILED, request, details));
  }

  @ExceptionHandler(NoResourceFoundException.class)
  ResponseEntity<ApiErrorResponse> handleNoResourceFound(
    NoResourceFoundException ex,
    HttpServletRequest request
  ) {
    return ResponseEntity
      .status(HttpStatus.NOT_FOUND)
      .body(buildResponse(ErrorCode.NOT_FOUND, request, null));
  }

  @ExceptionHandler(Exception.class)
  ResponseEntity<ApiErrorResponse> handleGeneric(
    Exception ex,
    HttpServletRequest request
  ) {
    log.error(
      "Unhandled exception for path {}: {}",
      request.getRequestURI(),
      ex.getMessage(),
      ex
    );
    return ResponseEntity
      .internalServerError()
      .body(buildResponse(ErrorCode.INTERNAL_ERROR, request, null));
  }

  private ApiErrorResponse buildResponse(
    ErrorCode errorCode,
    HttpServletRequest request,
    List<ApiFieldError> details
  ) {
    String message = messageSource.getMessage(
      errorCode.messageKey(),
      null,
      LocaleContextHolder.getLocale()
    );

    String spanTraceId = Span.current().getSpanContext().getTraceId();
    String traceId = ALL_ZEROS_TRACE_ID.equals(spanTraceId)
      ? null
      : spanTraceId;

    return new ApiErrorResponse(
      errorCode.name(),
      message,
      Instant.now(),
      request.getRequestURI(),
      traceId,
      details
    );
  }

  private static String toErrorCode(String constraintCode) {
    if (constraintCode == null) return null;
    return constraintCode.replaceAll("([a-z])([A-Z])", "$1_$2").toUpperCase();
  }
}
