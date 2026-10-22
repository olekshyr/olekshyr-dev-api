package dev.olekshyr.api.blog;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

record CreatePostRequest(
  @NotBlank @Size(max = 255) @Pattern(regexp = SlugFormat.REGEX) String slug
) {}
