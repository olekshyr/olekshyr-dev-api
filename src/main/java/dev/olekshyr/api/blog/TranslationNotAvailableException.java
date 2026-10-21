package dev.olekshyr.api.blog;

import dev.olekshyr.api.shared.ApiException;
import dev.olekshyr.api.shared.ErrorCode;
import dev.olekshyr.api.shared.SupportedLocale;
import org.springframework.http.HttpStatus;

/** Visible in some other locale, but not this one (D5). */
class TranslationNotAvailableException extends ApiException {

  TranslationNotAvailableException(String slug, SupportedLocale locale) {
    super(
      ErrorCode.TRANSLATION_NOT_AVAILABLE,
      HttpStatus.NOT_FOUND,
      "Post '" + slug + "' is not visible in " + locale.code()
    );
  }
}
