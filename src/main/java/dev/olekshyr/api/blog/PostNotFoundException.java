package dev.olekshyr.api.blog;

import dev.olekshyr.api.shared.ApiException;
import dev.olekshyr.api.shared.ErrorCode;
import org.springframework.http.HttpStatus;

/** Unknown, deleted, or visible in no locale (D5). */
class PostNotFoundException extends ApiException {

  PostNotFoundException(String slug) {
    super(
      ErrorCode.NOT_FOUND,
      HttpStatus.NOT_FOUND,
      "No visible post with slug '" + slug + "'"
    );
  }
}
