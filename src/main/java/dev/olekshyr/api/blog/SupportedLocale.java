package dev.olekshyr.api.blog;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

public enum SupportedLocale {
  EN,
  UK,
  JA;

  @Converter(autoApply = true)
  public static class PersistenceConverter
    implements AttributeConverter<SupportedLocale, String> {

    @Override
    public String convertToDatabaseColumn(SupportedLocale locale) {
      return locale == null ? null : locale.name().toLowerCase();
    }

    @Override
    public SupportedLocale convertToEntityAttribute(String value) {
      return value == null
        ? null
        : SupportedLocale.valueOf(value.toUpperCase());
    }
  }
}
