package dev.olekshyr.api.blog;

import dev.olekshyr.api.shared.SupportedLocale;
import java.time.Instant;
import java.util.List;

/** A row of the admin list (D16): no content, just each locale's status. */
record AdminPostListItem(
  long id,
  String slug,
  Instant updatedAt,
  Instant deletedAt,
  List<TranslationStatus> translations
) {
  AdminPostListItem {
    translations = List.copyOf(translations);
  }

  record TranslationStatus(
    SupportedLocale locale,
    PostStatus status,
    Instant updatedAt
  ) {}

  static AdminPostListItem of(Post post) {
    return new AdminPostListItem(
      post.getId(),
      post.getSlug(),
      post.getUpdatedAt(),
      post.getDeletedAt(),
      post
        .translationsInLocaleOrder()
        .stream()
        .map(t ->
          new TranslationStatus(t.getLocale(), t.getStatus(), t.getUpdatedAt())
        )
        .toList()
    );
  }
}
