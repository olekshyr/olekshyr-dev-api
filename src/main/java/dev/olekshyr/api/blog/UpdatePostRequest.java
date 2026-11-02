package dev.olekshyr.api.blog;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** PUT replaces the whole resource (D14); the slug is all a post has. */
record UpdatePostRequest(
  @Schema(
    description = "Lowercase letters and digits in words joined by single " +
    "hyphens; unique across posts, deleted ones included. Sending the current slug is a no-op.",
    example = "hello-world"
  )
  @NotBlank
  @Size(max = 255)
  @Pattern(regexp = SlugFormat.REGEX)
  String slug
) {}
