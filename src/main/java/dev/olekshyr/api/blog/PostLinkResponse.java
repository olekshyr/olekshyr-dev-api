package dev.olekshyr.api.blog;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "A link to another post, in the requested locale.")
record PostLinkResponse(
  @Schema(description = "The post's URL key.") String slug,
  String title
) {
  static PostLinkResponse of(PostTranslation translation) {
    return new PostLinkResponse(
      translation.getPost().getSlug(),
      translation.getTitle()
    );
  }
}
