package dev.olekshyr.api.blog;

public class PostDeletedException extends RuntimeException {

  public PostDeletedException(String slug) {
    super("Post '" + slug + "' is deleted and cannot be modified");
  }
}
