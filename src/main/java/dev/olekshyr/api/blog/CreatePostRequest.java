package dev.olekshyr.api.blog;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

record CreatePostRequest(
  @Schema(
    description = "Lowercase letters and digits in words joined by single " +
    "hyphens; unique across posts, deleted ones included.",
    example = "hello-world"
  )
  @NotBlank
  @Size(max = 255)
  @Pattern(regexp = SlugFormat.REGEX)
  String slug
) {}
