package dev.olekshyr.api.blog;

import dev.olekshyr.api.shared.SupportedLocale;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Map;

/**
 * names is keyed by locale code ({"en": "Java"}) and replaces every name; a
 * locale left out loses its name, and the tag shows its slug there (D6).
 */
record UpsertTagRequest(
  @NotBlank @Size(max = 255) @Pattern(regexp = SlugFormat.REGEX) String slug,
  @Pattern(regexp = "^#[0-9a-fA-F]{6}$") String color,
  @NotNull Map<SupportedLocale, @NotBlank @Size(max = 100) String> names
) {}
