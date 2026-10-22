package dev.olekshyr.api.blog;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/** The post's complete tag set; an empty list removes every tag. */
record SetPostTagsRequest(
  @NotNull @Size(max = 50) List<@NotBlank String> tagSlugs
) {}
