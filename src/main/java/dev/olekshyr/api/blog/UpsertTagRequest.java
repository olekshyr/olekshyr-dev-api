package dev.olekshyr.api.blog;

import dev.olekshyr.api.shared.SupportedLocale;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * names is keyed by locale code ({"en": "Java"}) and replaces every name; a
 * locale left out loses its name, and the tag shows its slug there (D6).
 */
record UpsertTagRequest(
  @Schema(
    description = "Lowercase letters and digits in words joined by single " +
    "hyphens; unique across tags.",
    example = "java"
  )
  @NotBlank
  @Size(max = 255)
  @Pattern(regexp = SlugFormat.REGEX)
  String slug,
  @Schema(description = "`#RRGGBB`.", example = "#3366ff")
  @Pattern(regexp = "^#[0-9a-fA-F]{6}$")
  @Nullable
  String color,
  @Schema(
    description = "Name per locale code (`en`, `uk`, `ja`, any case), each " +
    "at most 100 characters and unique among the tags in that locale. " +
    "Replaces every name: a locale left out loses its name. Another key is " +
    "a `VALIDATION_FAILED` without details.",
    example = "{\"en\": \"Java\", \"uk\": \"Java\"}"
  )
  @NotNull
  Map<SupportedLocale, @NotBlank @Size(max = 100) String> names
) {}
