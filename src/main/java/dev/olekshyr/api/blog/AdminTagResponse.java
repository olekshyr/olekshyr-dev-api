package dev.olekshyr.api.blog;

import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Every name with its locale code, in en, uk, ja order (D15, D18). */
record AdminTagResponse(
  long id,
  String slug,
  String color,
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
