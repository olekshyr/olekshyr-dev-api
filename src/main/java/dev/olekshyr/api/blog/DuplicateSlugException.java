package dev.olekshyr.api.blog;

import dev.olekshyr.api.shared.ApiException;
import dev.olekshyr.api.shared.ErrorCode;
import org.springframework.http.HttpStatus;

/** Post and tag slugs are unique, deleted posts' included (D10). */
class DuplicateSlugException extends ApiException {

  DuplicateSlugException(String slug) {
    super(
      ErrorCode.DUPLICATE_SLUG,
      HttpStatus.CONFLICT,
      "Slug '" + slug + "' is already taken"
    );
  }
}
