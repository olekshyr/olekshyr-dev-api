package dev.olekshyr.api.blog;

import dev.olekshyr.api.shared.SupportedLocale;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import org.jspecify.annotations.Nullable;

/** A row of the admin list (D16): no content, just each locale's status. */
@Schema(description = "A row of the admin post list: each locale's status.")
record AdminPostListItem(
  long id,
  String slug,
  @Schema(description = "The list is sorted by it, newest first.")
  Instant updatedAt,
  @Schema(
    description = "When the post was deleted; null while it isn't. A " +
    "deleted post can't be written to or restored."
  )
  @Nullable
  Instant deletedAt,
  @Schema(
    description = "In `en, uk, ja` order; a locale without content is absent."
  )
  List<TranslationStatus> translations
) {
  AdminPostListItem {
    translations = List.copyOf(translations);
  }

  @Schema(name = "AdminTranslationStatus")
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
