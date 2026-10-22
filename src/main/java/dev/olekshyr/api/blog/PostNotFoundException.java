package dev.olekshyr.api.blog;

import dev.olekshyr.api.shared.ApiException;
import dev.olekshyr.api.shared.ErrorCode;
import org.springframework.http.HttpStatus;

/** Public: unknown, deleted, or visible in no locale (D5). */
class PostNotFoundException extends ApiException {

  PostNotFoundException(String slug) {
    super(
      ErrorCode.NOT_FOUND,
      HttpStatus.NOT_FOUND,
      "No visible post with slug '" + slug + "'"
    );
  }

  /** Admin lookup by id; deleted posts are still found there (D23). */
  PostNotFoundException(long id) {
    super(ErrorCode.NOT_FOUND, HttpStatus.NOT_FOUND, "No post with id " + id);
  }
}
