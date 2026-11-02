package dev.olekshyr.api.blog;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;

@Schema(description = "A post in the list, in the requested locale.")
record PostSummaryResponse(
  @Schema(description = "The post's URL key, the same in every locale.")
  String slug,
  String title,
  @Schema(
    description = "Plain text; escape it. Always set: publishing needs it."
  )
  String excerpt,
  @Schema(description = "Sorted by slug.") List<TagResponse> tags,
  @Schema(
    description = "When the translation was first published; re-publishing " +
    "keeps it. The list is sorted by it, newest first."
  )
  Instant publishedAt,
  @Schema(
    description = "Estimated minutes, at least 1: 200 words a minute, or " +
    "500 characters for `ja`."
  )
  int readTimeMinutes
) {
  PostSummaryResponse {
    tags = List.copyOf(tags);
  }

  static PostSummaryResponse of(PostTranslation translation) {
    Post post = translation.getPost();
    return new PostSummaryResponse(
      post.getSlug(),
      translation.getTitle(),
      translation.getExcerpt(),
      TagResponse.listOf(post.getTags(), translation.getLocale()),
      translation.getPublishedAt(),
      ReadTimeCalculator.minutes(translation.getBody(), translation.getLocale())
    );
  }
}
