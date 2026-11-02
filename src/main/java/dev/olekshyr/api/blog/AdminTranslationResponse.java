package dev.olekshyr.api.blog;

import dev.olekshyr.api.shared.SupportedLocale;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import org.jspecify.annotations.Nullable;

/** Stored values as they are: no metaTitle fallback (D7 is public-only). */
@Schema(description = "A translation as stored, with no public fallbacks.")
record AdminTranslationResponse(
  SupportedLocale locale,
  String title,
  @Nullable String excerpt,
  @Schema(description = "Sanitized HTML, as saved.") String body,
  @Schema(description = "As saved; the public API falls back to `title`.")
  @Nullable
  String metaTitle,
  @Nullable String metaDescription,
  @Schema(description = "`PUBLISHED` translations are live (public).")
  PostStatus status,
  @Schema(
    description = "First publish; null until then. Unpublishing keeps it, " +
    "and re-publishing restores it."
  )
  @Nullable
  Instant publishedAt,
  @Schema(description = "Publishing and unpublishing move it too.")
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
