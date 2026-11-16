package dev.olekshyr.api.blog;

import dev.olekshyr.api.shared.SupportedLocale;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;

@Schema(
  description = "A post in the list. Its content is in the requested locale " +
  "when `translated` is true, otherwise in the locale named by `locale`."
)
record PostSummaryResponse(
  @Schema(description = "The post's URL key, the same in every locale.")
  String slug,
  @Schema(description = "In `locale`.") String title,
  @Schema(
    description = "Plain text in `locale`; escape it. Always set: " +
    "publishing needs it."
  )
  String excerpt,
  @Schema(
    description = "Sorted by slug. Names are in the requested locale (the " +
    "slug when a tag has none), even when `translated` is false."
  )
  List<TagResponse> tags,
  @Schema(
    description = "When the translation in `locale` was first published; " +
    "re-publishing keeps it."
  )
  Instant publishedAt,
  @Schema(
    description = "Estimated minutes for the translation in `locale`, at " +
    "least 1: 200 words a minute, or 500 characters for `ja`."
  )
  int readTimeMinutes,
  @Schema(
    description = "Whether the post is published in the requested locale. " +
    "Always true unless the list was requested with `include=untranslated`."
  )
  boolean translated,
  @Schema(
    description = "The locale of `title`, `excerpt`, `publishedAt` and " +
    "`readTimeMinutes`: the requested one when `translated`, otherwise the " +
    "first of `availableLocales`."
  )
  SupportedLocale locale,
  @Schema(
    description = "Every locale the post is visible in, in `en, uk, ja` " +
    "order: the hreflang alternates."
  )
  List<SupportedLocale> availableLocales
) {
  PostSummaryResponse {
    tags = List.copyOf(tags);
    availableLocales = List.copyOf(availableLocales);
  }

  /**
   * @param published every published translation of one visible post
   * @param locale the requested locale; its translation is shown when there
   *     is one, otherwise the first in {@code en, uk, ja} order (D28)
   */
  static PostSummaryResponse of(
    List<PostTranslation> published,
    SupportedLocale locale
  ) {
    List<PostTranslation> ordered = published
      .stream()
      .sorted(Comparator.comparing(PostTranslation::getLocale))
      .toList();
    PostTranslation shown = ordered
      .stream()
      .filter(t -> t.getLocale() == locale)
      .findFirst()
      .orElse(ordered.getFirst());
    Post post = shown.getPost();
    return new PostSummaryResponse(
      post.getSlug(),
      shown.getTitle(),
      shown.getExcerpt(),
      TagResponse.listOf(post.getTags(), locale),
      shown.getPublishedAt(),
      ReadTimeCalculator.minutes(shown.getBody(), shown.getLocale()),
      shown.getLocale() == locale,
      shown.getLocale(),
      ordered.stream().map(PostTranslation::getLocale).toList()
    );
  }
}
