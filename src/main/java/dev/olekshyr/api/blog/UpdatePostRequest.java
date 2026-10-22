package dev.olekshyr.api.blog;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** PUT replaces the whole resource (D14); the slug is all a post has. */
record UpdatePostRequest(
  @NotBlank @Size(max = 255) @Pattern(regexp = SlugFormat.REGEX) String slug
) {}
