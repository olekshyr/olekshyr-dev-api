package dev.olekshyr.api.shared;

import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.examples.Example;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import java.lang.reflect.AnnotatedElement;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springdoc.core.customizers.GlobalOperationCustomizer;
import org.springframework.context.MessageSource;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.method.HandlerMethod;

/**
 * Turns an endpoint's {@link ApiError}s, plus the 500 every endpoint can
 * return, into one response per status, each listing its codes with an
 * example per code in the default locale.
 */
class ApiErrorOperationCustomizer implements GlobalOperationCustomizer {

  static final String ERROR_SCHEMA_REF =
    "#/components/schemas/ApiErrorResponse";

  private static final Instant EXAMPLE_TIME = Instant.parse(
    "2026-01-01T00:00:00Z"
  );

  private static final DocumentedError INTERNAL_ERROR = new DocumentedError(
    HttpStatus.INTERNAL_SERVER_ERROR,
    ErrorCode.INTERNAL_ERROR,
    "An unexpected server error."
  );

  private final MessageSource messageSource;

  ApiErrorOperationCustomizer(MessageSource messageSource) {
    this.messageSource = messageSource;
  }

  @Override
  public Operation customize(Operation operation, HandlerMethod handlerMethod) {
    Map<HttpStatus, List<DocumentedError>> byStatus = Stream
      .concat(
        AnnotatedElementUtils
          .findMergedRepeatableAnnotations(
            handlerMethod.getMethod(),
            ApiError.class
          )
          .stream()
          .map(e -> new DocumentedError(e.status(), e.code(), e.when())),
        Stream.of(INTERNAL_ERROR)
      )
      .collect(
        Collectors.groupingBy(
          DocumentedError::status,
          TreeMap::new,
          Collectors.toList()
        )
      );
    String path = path(handlerMethod);
    byStatus.forEach((status, errors) ->
      operation
        .getResponses()
        .addApiResponse(String.valueOf(status.value()), response(errors, path))
    );
    return operation;
  }

  private ApiResponse response(List<DocumentedError> errors, String path) {
    MediaType body = new MediaType()
      .schema(new Schema<>().$ref(ERROR_SCHEMA_REF));
    errors
      .stream()
      .map(DocumentedError::code)
      .distinct()
      .forEach(code ->
        body.addExamples(code.name(), new Example().value(example(code, path)))
      );
    return new ApiResponse()
      .description(
        errors
          .stream()
          .map(e -> "- `" + e.code() + "`: " + e.when())
          .collect(Collectors.joining("\n"))
      )
      .content(
        new Content()
          .addMediaType(
            org.springframework.http.MediaType.APPLICATION_JSON_VALUE,
            body
          )
      );
  }

  private Map<String, Object> example(ErrorCode code, String path) {
    Map<String, Object> example = new LinkedHashMap<>();
    example.put("code", code.name());
    example.put(
      "message",
      messageSource.getMessage(
        code.messageKey(),
        null,
        SupportedLocale.DEFAULT.toLocale()
      )
    );
    example.put("timestamp", EXAMPLE_TIME.toString());
    example.put("path", path);
    return example;
  }

  /** The mapping's path template, e.g. /api/v1/posts/{slug}. */
  private static String path(HandlerMethod handlerMethod) {
    return (
      firstPath(handlerMethod.getBeanType()) +
      firstPath(handlerMethod.getMethod())
    );
  }

  private static String firstPath(AnnotatedElement element) {
    RequestMapping mapping = AnnotatedElementUtils.findMergedAnnotation(
      element,
      RequestMapping.class
    );
    return mapping == null || mapping.path().length == 0
      ? ""
      : mapping.path()[0];
  }

  private record DocumentedError(
    HttpStatus status,
    ErrorCode code,
    String when
  ) {}
}
