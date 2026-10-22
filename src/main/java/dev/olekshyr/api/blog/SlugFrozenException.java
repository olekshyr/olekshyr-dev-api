package dev.olekshyr.api.blog;

import dev.olekshyr.api.shared.ApiException;
import dev.olekshyr.api.shared.ErrorCode;
import org.springframework.http.HttpStatus;

/** The slug is fixed once any translation has been published (D10). */
class SlugFrozenException extends ApiException {

  SlugFrozenException(String slug) {
    super(
      ErrorCode.SLUG_FROZEN,
      HttpStatus.CONFLICT,
      "Slug of post '" + slug + "' is frozen because it has been published"
    );
  }
}
