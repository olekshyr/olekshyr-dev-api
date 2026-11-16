package dev.olekshyr.api.blog;

import dev.olekshyr.api.shared.SupportedLocale;
import io.swagger.v3.oas.annotations.media.Schema;
import org.jspecify.annotations.Nullable;

@Schema(description = "A tag that at least one listed post carries.")
record TagSummaryResponse(
  @Schema(description = "Stable across locales; the `tag` filter takes it.")
  String slug,
  @Schema(
    description = "Name in the requested locale; the slug when it has none."
  )
  String name,
  @Schema(description = "`#RRGGBB`.", example = "#3366ff")
  @Nullable
  String color,
  @Schema(
    description = "How many posts the post list returns for this tag, with " +
    "the same `include` and locale. At least 1."
  )
  long postCount
) {
  static TagSummaryResponse of(
    Tag tag,
    SupportedLocale locale,
    long postCount
  ) {
    TagResponse response = TagResponse.of(tag, locale);
    return new TagSummaryResponse(
      response.slug(),
      response.name(),
      response.color(),
      postCount
    );
  }
}
