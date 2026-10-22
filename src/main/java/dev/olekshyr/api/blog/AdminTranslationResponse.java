package dev.olekshyr.api.blog;

import dev.olekshyr.api.shared.SupportedLocale;
import java.time.Instant;

/** Stored values as they are: no metaTitle fallback (D7 is public-only). */
record AdminTranslationResponse(
  SupportedLocale locale,
  String title,
  String excerpt,
  String body,
  String metaTitle,
  String metaDescription,
  PostStatus status,
  Instant publishedAt,
  Instant updatedAt
) {
  static AdminTranslationResponse of(PostTranslation translation) {
    return new AdminTranslationResponse(
      translation.getLocale(),
      translation.getTitle(),
      translation.getExcerpt(),
      translation.getBody(),
      translation.getMetaTitle(),
      translation.getMetaDescription(),
      translation.getStatus(),
      translation.getPublishedAt(),
      translation.getUpdatedAt()
    );
  }
}
