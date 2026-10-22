package dev.olekshyr.api.blog;

import java.time.Instant;
import java.util.List;

/**
 * A post with every translation, in any status (D15). Every post write returns
 * it, so each locale tab can refresh all statuses.
 */
record AdminPostResponse(
  long id,
  String slug,
  Instant createdAt,
  Instant updatedAt,
  Instant deletedAt,
  List<AdminTagResponse> tags,
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
