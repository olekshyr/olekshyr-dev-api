package dev.olekshyr.api.blog;

import dev.olekshyr.api.shared.ApiException;
import dev.olekshyr.api.shared.ErrorCode;
import org.springframework.http.HttpStatus;

/** Deleted posts count too: they keep their tags. */
class TagInUseException extends ApiException {

  TagInUseException(String slug, long postCount) {
    super(
      ErrorCode.TAG_IN_USE,
      HttpStatus.CONFLICT,
      "Tag '" + slug + "' is used by " + postCount + " post(s)"
    );
  }
}
