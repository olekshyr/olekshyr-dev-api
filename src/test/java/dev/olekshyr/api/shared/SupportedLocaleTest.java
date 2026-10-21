package dev.olekshyr.api.shared;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;
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
  void serializesAsLowercaseCode() {
    String json = JsonMapper
      .builder()
      .build()
      .writeValueAsString(List.of(SupportedLocale.EN, SupportedLocale.JA));

    assertThat(json).isEqualTo("[\"en\",\"ja\"]");
  }
}
