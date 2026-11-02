package dev.olekshyr.api.blog;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/** The post's complete tag set; an empty list removes every tag. */
record SetPostTagsRequest(
  @Schema(
    description = "Slugs of existing tags; replaces the whole set, so `[]` " +
    "removes every tag.",
    example = "[\"java\", \"spring\"]"
  )
  @NotNull
  @Size(max = 50)
  List<@NotBlank String> tagSlugs
) {}
