package dev.olekshyr.api.blog;

import dev.olekshyr.api.shared.SupportedLocale;
import java.time.Instant;
import java.util.List;

record PostDetailResponse(
  String slug,
  String title,
  String excerpt,
  String body,
  String metaTitle,
  String metaDescription,
  List<TagResponse> tags,
  Instant publishedAt,
  Instant updatedAt,
  int readTimeMinutes,
  List<SupportedLocale> availableLocales
) {
  PostDetailResponse {
    tags = List.copyOf(tags);
    availableLocales = List.copyOf(availableLocales);
  }

  static PostDetailResponse of(
    PostTranslation translation,
    List<SupportedLocale> availableLocales
  ) {
    Post post = translation.getPost();
    String metaTitle = translation.getMetaTitle();
    return new PostDetailResponse(
      post.getSlug(),
      translation.getTitle(),
      translation.getExcerpt(),
      translation.getBody(),
      metaTitle == null || metaTitle.isBlank()
        ? translation.getTitle()
        : metaTitle,
      translation.getMetaDescription(),
      TagResponse.listOf(post.getTags(), translation.getLocale()),
      translation.getPublishedAt(),
      translation.getUpdatedAt(),
      ReadTimeCalculator.minutes(
        translation.getBody(),
        translation.getLocale()
      ),
      availableLocales
    );
  }
}
