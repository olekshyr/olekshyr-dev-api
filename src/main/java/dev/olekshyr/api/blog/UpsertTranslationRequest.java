package dev.olekshyr.api.blog;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;

/**
 * Saving a draft needs only title and body; publishing also needs excerpt and
 * metaDescription (D9). Lengths match the columns; body is TEXT, capped here.
 */
record UpsertTranslationRequest(
  @Schema(description = "Plain text.") @NotBlank @Size(max = 255) String title,
  @Schema(description = "Plain text; required to publish and while published.")
  @Size(max = 500)
  @Nullable
  String excerpt,
  @Schema(
    description = "HTML from CKEditor 5. Sanitized on save: anything outside " +
    "the allowed elements (scripts, event handlers, inline styles, non-https " +
    "images, …) is removed; a body with nothing left is a `NOT_BLANK` error."
  )
  @NotBlank
  @Size(max = 200_000)
  String body,
  @Schema(description = "Plain text; the public API falls back to `title`.")
  @Size(max = 255)
  @Nullable
  String metaTitle,
  @Schema(description = "Plain text; required to publish and while published.")
  @Size(max = 320)
  @Nullable
  String metaDescription
) {
  PostContent toContent(String sanitizedBody) {
    return new PostContent(
      title,
      excerpt,
      sanitizedBody,
      metaTitle,
      metaDescription
    );
  }
}
