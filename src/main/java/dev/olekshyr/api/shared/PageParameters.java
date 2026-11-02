package dev.olekshyr.api.shared;

import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Schema;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Documents page and size on a list endpoint whose Pageable parameter is
 * hidden. Any sort parameter is ignored, so it isn't documented (D4).
 */
@Documented
@Target({ ElementType.METHOD, ElementType.ANNOTATION_TYPE })
@Retention(RetentionPolicy.RUNTIME)
@Parameter(
  in = ParameterIn.QUERY,
  name = "page",
  description = "Zero-based page index. A negative or unparseable value falls " +
  "back to 0; a page past the end returns empty `content`.",
  schema = @Schema(type = "integer", defaultValue = "0", minimum = "0")
)
@Parameter(
  in = ParameterIn.QUERY,
  name = "size",
  description = "Page size. Larger values are capped at 50; a value below 1 " +
  "or unparseable falls back to 10.",
  schema = @Schema(
    type = "integer",
    defaultValue = "10",
    minimum = "1",
    maximum = "50"
  )
)
public @interface PageParameters {
}
