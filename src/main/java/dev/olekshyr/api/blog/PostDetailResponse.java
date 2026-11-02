package dev.olekshyr.api.blog;

import dev.olekshyr.api.shared.SupportedLocale;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;

@Schema(description = "A post in the requested locale.")
record PostDetailResponse(
  @Schema(description = "The post's URL key, the same in every locale.")
  String slug,
  String title,
  @Schema(
    description = "Plain text; escape it. Always set: publishing needs it."
  )
  String excerpt,
  @Schema(
    description = "Sanitized HTML (CKEditor 5 content), safe to render as-is."
  )
  String body,
  @Schema(description = "For `<title>`; falls back to `title` when not set.")
  String metaTitle,
  @Schema(
    description = "For `<meta name=\"description\">`. Always set: " +
    "publishing needs it."
  )
  String metaDescription,
  @Schema(description = "Sorted by slug.") List<TagResponse> tags,
  @Schema(
    description = "When the translation was first published (JSON-LD " +
    "`datePublished`); re-publishing keeps it."
  )
  Instant publishedAt,
  @Schema(
    description = "Last change to the translation (JSON-LD `dateModified`). " +
    "Publishing and unpublishing move it too."
  )
  Instant updatedAt,
  @Schema(
    description = "Estimated minutes, at least 1: 200 words a minute, or " +
    "500 characters for `ja`."
  )
  int readTimeMinutes,
  @Schema(
    description = "Every locale the post is visible in, this one included, " +
    "in `en, uk, ja` order: the hreflang alternates."
  )
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
