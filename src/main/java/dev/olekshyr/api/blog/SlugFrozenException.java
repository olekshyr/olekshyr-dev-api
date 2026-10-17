package dev.olekshyr.api.blog;

public class SlugFrozenException extends RuntimeException {

  public SlugFrozenException(String slug) {
    super(
      "Slug of post '" + slug + "' is frozen because it has been published"
    );
  }
}
