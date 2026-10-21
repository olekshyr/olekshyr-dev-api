package dev.olekshyr.api.shared;

import com.fasterxml.jackson.annotation.JsonValue;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/** Declaration order is the canonical order of locale lists (D18). */
public enum SupportedLocale {
  EN,
  UK,
  JA;

  public static final SupportedLocale DEFAULT = EN;

  /** Lowercase BCP 47 language tag, as used in JSON and Content-Language. */
  @JsonValue
  public String code() {
    return name().toLowerCase(Locale.ROOT);
  }

  public Locale toLocale() {
    return Locale.forLanguageTag(code());
  }

  /** Matches on language only; anything unsupported maps to the default. */
  public static SupportedLocale from(Locale locale) {
    return Arrays
      .stream(values())
      .filter(l -> l.code().equals(locale.getLanguage()))
      .findFirst()
      .orElse(DEFAULT);
  }

  static List<Locale> javaLocales() {
    return Arrays.stream(values()).map(SupportedLocale::toLocale).toList();
  }

  @Converter(autoApply = true)
  public static class PersistenceConverter
    implements AttributeConverter<SupportedLocale, String> {

    @Override
    public String convertToDatabaseColumn(SupportedLocale locale) {
      return locale == null ? null : locale.code();
    }

    @Override
    public SupportedLocale convertToEntityAttribute(String value) {
      return value == null
        ? null
        : SupportedLocale.valueOf(value.toUpperCase(Locale.ROOT));
    }
  }
}
