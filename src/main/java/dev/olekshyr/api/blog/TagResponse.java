package dev.olekshyr.api.blog;

import dev.olekshyr.api.shared.SupportedLocale;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

record TagResponse(String slug, String name, String color) {
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
