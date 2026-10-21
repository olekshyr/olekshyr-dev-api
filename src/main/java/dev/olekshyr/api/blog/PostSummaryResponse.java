package dev.olekshyr.api.blog;

import java.time.Instant;
import java.util.List;

record PostSummaryResponse(
  String slug,
  String title,
  String excerpt,
  List<TagResponse> tags,
  Instant publishedAt,
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
