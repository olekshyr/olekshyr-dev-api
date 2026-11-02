package dev.olekshyr.api.blog;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * A post with every translation, in any status (D15). Every post write returns
 * it, so each locale tab can refresh all statuses.
 */
@Schema(description = "A post with every translation, in any status.")
record AdminPostResponse(
  @Schema(description = "Admin URLs address the post by this id.") long id,
  @Schema(
    description = "Public URL key. Frozen once any translation was published."
  )
  String slug,
  Instant createdAt,
  @Schema(description = "Last change to the post or any of its translations.")
  Instant updatedAt,
  @Schema(
    description = "When the post was deleted; null while it isn't. A " +
    "deleted post can't be written to or restored."
  )
  @Nullable
  Instant deletedAt,
  @Schema(description = "Sorted by slug.") List<AdminTagResponse> tags,
  @Schema(
    description = "One per locale the post has content in, in `en, uk, ja` " +
    "order; a locale without content is absent."
  )
  List<AdminTranslationResponse> translations
) {
  AdminPostResponse {
    tags = List.copyOf(tags);
    translations = List.copyOf(translations);
  }

  static AdminPostResponse of(Post post) {
    return new AdminPostResponse(
      post.getId(),
      post.getSlug(),
      post.getCreatedAt(),
      post.getUpdatedAt(),
      post.getDeletedAt(),
      AdminTagResponse.listOf(post.getTags()),
      post
        .translationsInLocaleOrder()
        .stream()
        .map(AdminTranslationResponse::of)
        .toList()
    );
  }
}
