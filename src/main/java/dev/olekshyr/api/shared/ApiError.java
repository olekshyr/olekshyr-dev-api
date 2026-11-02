package dev.olekshyr.api.shared;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.http.HttpStatus;

/**
 * Documents an error an endpoint returns, for the OpenAPI spec. Codes sharing
 * a status become one response (see {@code OpenApiConfig}). Every operation
 * also gets 500 and its tier's 401/403 without one.
 */
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Repeatable(ApiErrors.class)
public @interface ApiError {
  HttpStatus status();

  ErrorCode code();

  /** When the endpoint returns it; Markdown. */
  String when();
}
