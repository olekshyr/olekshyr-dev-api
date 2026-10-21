package dev.olekshyr.api.blog;

import dev.olekshyr.api.shared.SupportedLocale;

public class TranslationNotFoundException extends RuntimeException {

  private final SupportedLocale locale;

  public TranslationNotFoundException(SupportedLocale locale) {
    super("No translation for locale " + locale);
    this.locale = locale;
  }

  public SupportedLocale getLocale() {
    return locale;
  }
}
