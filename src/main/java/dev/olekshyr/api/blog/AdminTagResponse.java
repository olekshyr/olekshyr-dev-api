package dev.olekshyr.api.blog;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/** Every name with its locale code, in en, uk, ja order (D15, D18). */
@Schema(description = "A tag with every name.")
record AdminTagResponse(
  long id,
  String slug,
  @Schema(description = "`#RRGGBB`.", example = "#3366ff")
  @Nullable
  String color,
  @Schema(
    description = "Name per locale code (`en`, `uk`, `ja`), in that order. A " +
    "locale without a name is absent; the public API shows the slug there.",
    example = "{\"en\": \"Java\", \"uk\": \"Java\"}"
  )
  Map<String, String> names
) {
  AdminTagResponse {
    names = Collections.unmodifiableMap(new LinkedHashMap<>(names));
  }

  static AdminTagResponse of(Tag tag) {
    Map<String, String> names = new LinkedHashMap<>();
    tag.names().forEach((locale, name) -> names.put(locale.code(), name));
    return new AdminTagResponse(
      tag.getId(),
      tag.getSlug(),
      tag.getColor(),
      names
    );
  }

  static List<AdminTagResponse> listOf(Collection<Tag> tags) {
    return tags
      .stream()
      .sorted(Comparator.comparing(Tag::getSlug))
      .map(AdminTagResponse::of)
      .toList();
  }
}
