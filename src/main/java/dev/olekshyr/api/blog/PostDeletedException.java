package dev.olekshyr.api.blog;

import dev.olekshyr.api.shared.ApiException;
import dev.olekshyr.api.shared.ErrorCode;
import org.springframework.http.HttpStatus;

/** Writes to a deleted post: it can't come back, so it's gone (D23). */
public class PostDeletedException extends ApiException {

  public PostDeletedException(String slug) {
    super(
      ErrorCode.NOT_FOUND,
      HttpStatus.NOT_FOUND,
      "Post '" + slug + "' is deleted and cannot be modified"
    );
  }
}
