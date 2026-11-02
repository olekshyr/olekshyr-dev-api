package dev.olekshyr.api.blog;

import dev.olekshyr.api.shared.SupportedLocale;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import org.jspecify.annotations.Nullable;

@Schema(description = "A tag in the requested locale.")
record TagResponse(
  @Schema(description = "Stable across locales.") String slug,
  @Schema(
    description = "Name in the requested locale; the slug when it has none."
  )
  String name,
  @Schema(description = "`#RRGGBB`.", example = "#3366ff")
  @Nullable
  String color
) {
  /** A tag without a name in the locale shows its slug, never disappears (D6). */
  static TagResponse of(Tag tag, SupportedLocale locale) {
    return new TagResponse(
      tag.getSlug(),
      tag.nameIn(locale).orElse(tag.getSlug()),
      tag.getColor()
    );
  }

  /** Sorted by slug: stable across locales (D6). */
  static List<TagResponse> listOf(
    Collection<Tag> tags,
    SupportedLocale locale
  ) {
    return tags
      .stream()
      .sorted(Comparator.comparing(Tag::getSlug))
      .map(tag -> of(tag, locale))
      .toList();
  }
}
