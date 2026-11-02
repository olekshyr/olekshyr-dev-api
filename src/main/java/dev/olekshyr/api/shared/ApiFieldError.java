package dev.olekshyr.api.shared;

import io.swagger.v3.oas.annotations.media.Schema;
import org.jspecify.annotations.Nullable;

/**
 * code and message come from Bean Validation for its checks, which may leave
 * them out (a constraint without a code, or without a message).
 */
public record ApiFieldError(
  @Schema(
    description = "Request field, with an index or key for an item.",
    example = "tagSlugs[1]"
  )
  String field,
  @Schema(
    description = "Stable field-level code: `NOT_BLANK`, `NOT_NULL`, `SIZE`, " +
    "`PATTERN` (the broken rule) or `UNKNOWN_TAG`. Null only for a rule " +
    "without a code.",
    example = "NOT_BLANK"
  )
  @Nullable
  String code,
  @Schema(description = "Localized from `Accept-Language`.")
  @Nullable
  String message
) {}
