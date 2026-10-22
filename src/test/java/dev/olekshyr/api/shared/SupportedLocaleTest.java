package dev.olekshyr.api.shared;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

class SupportedLocaleTest {

  @Test
  void codeIsLowercaseLanguageTag() {
    assertThat(SupportedLocale.UK.code()).isEqualTo("uk");
  }

  @Test
  void fromLocaleMatchesOnLanguage() {
    assertThat(SupportedLocale.from(Locale.forLanguageTag("ja-JP")))
      .isEqualTo(SupportedLocale.JA);
  }

  @Test
  void fromUnsupportedLocaleFallsBackToEnglish() {
    assertThat(SupportedLocale.from(Locale.FRENCH))
      .isEqualTo(SupportedLocale.EN);
  }

  @Test
  void fromCodeIsCaseInsensitive() {
    assertThat(SupportedLocale.fromCode("uk")).isEqualTo(SupportedLocale.UK);
    assertThat(SupportedLocale.fromCode("JA")).isEqualTo(SupportedLocale.JA);
  }

  @Test
  void fromCodeRejectsUnsupportedLocales() {
    assertThatThrownBy(() -> SupportedLocale.fromCode("fr"))
      .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void deserializesFromCodeAsValueAndMapKey() {
    JsonMapper mapper = JsonMapper.builder().build();

    assertThat(mapper.readValue("\"uk\"", SupportedLocale.class))
      .isEqualTo(SupportedLocale.UK);
    assertThat(
      mapper.readValue(
        "{\"en\":\"Java\",\"ja\":\"ジャバ\"}",
        new TypeReference<Map<SupportedLocale, String>>() {}
      )
    )
      .containsOnly(
        Map.entry(SupportedLocale.EN, "Java"),
        Map.entry(SupportedLocale.JA, "ジャバ")
      );
  }

  @Test
  void serializesAsLowercaseCode() {
    String json = JsonMapper
      .builder()
      .build()
      .writeValueAsString(List.of(SupportedLocale.EN, SupportedLocale.JA));

    assertThat(json).isEqualTo("[\"en\",\"ja\"]");
  }
}
