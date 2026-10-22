package dev.olekshyr.api.blog;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Saving a draft needs only title and body; publishing also needs excerpt and
 * metaDescription (D9). Lengths match the columns; body is TEXT, capped here.
 */
record UpsertTranslationRequest(
  @NotBlank @Size(max = 255) String title,
  @Size(max = 500) String excerpt,
  @NotBlank @Size(max = 200_000) String body,
  @Size(max = 255) String metaTitle,
  @Size(max = 320) String metaDescription
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
