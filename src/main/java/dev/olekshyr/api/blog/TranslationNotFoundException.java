package dev.olekshyr.api.blog;

import dev.olekshyr.api.shared.ApiException;
import dev.olekshyr.api.shared.ErrorCode;
import dev.olekshyr.api.shared.SupportedLocale;
import org.springframework.http.HttpStatus;

/** Admin publish/unpublish of a locale the post has no translation row in. */
public class TranslationNotFoundException extends ApiException {

  private final SupportedLocale locale;

  public TranslationNotFoundException(SupportedLocale locale) {
    super(
      ErrorCode.NOT_FOUND,
      HttpStatus.NOT_FOUND,
      "No translation for locale " + locale
    );
    this.locale = locale;
  }

  public SupportedLocale getLocale() {
    return locale;
  }
}
