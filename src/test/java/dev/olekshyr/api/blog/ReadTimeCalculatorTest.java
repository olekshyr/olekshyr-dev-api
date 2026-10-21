package dev.olekshyr.api.blog;

import static org.assertj.core.api.Assertions.assertThat;

import dev.olekshyr.api.shared.SupportedLocale;
import org.junit.jupiter.api.Test;

class ReadTimeCalculatorTest {

  private static String words(int count, String template) {
    return String.join(" ", java.util.Collections.nCopies(count, template));
  }

  @Test
  void twoHundredWordsIsOneMinute() {
    assertThat(
      ReadTimeCalculator.minutes(words(200, "word"), SupportedLocale.EN)
    )
      .isEqualTo(1);
  }

  @Test
  void partialMinuteRoundsUp() {
    assertThat(
      ReadTimeCalculator.minutes(words(201, "word"), SupportedLocale.UK)
    )
      .isEqualTo(2);
  }

  @Test
  void emptyBodyIsAtLeastOneMinute() {
    assertThat(ReadTimeCalculator.minutes("", SupportedLocale.EN)).isEqualTo(1);
  }

  @Test
  void markupIsNotCounted() {
    String html = words(250, "<span class=\"highlight token\">word</span>");

    assertThat(ReadTimeCalculator.minutes(html, SupportedLocale.EN))
      .isEqualTo(2);
  }

  @Test
  void tagsSeparateWords() {
    String html = "<p>" + "word</p><p>".repeat(200) + "word</p>";

    assertThat(ReadTimeCalculator.minutes(html, SupportedLocale.EN))
      .isEqualTo(2);
  }

  @Test
  void japaneseCountsCharactersAtFiveHundredPerMinute() {
    assertThat(ReadTimeCalculator.minutes("あ".repeat(500), SupportedLocale.JA))
      .isEqualTo(1);
    assertThat(ReadTimeCalculator.minutes("あ".repeat(501), SupportedLocale.JA))
      .isEqualTo(2);
  }

  @Test
  void japaneseIgnoresMarkupAndWhitespace() {
    String html = "<p class=\"lead\">" + "あ い\n".repeat(250) + "</p>";

    assertThat(ReadTimeCalculator.minutes(html, SupportedLocale.JA))
      .isEqualTo(1);
  }

  @Test
  void entitiesSeparateWords() {
    String html = "word&nbsp;".repeat(200) + "word&#160;word";

    assertThat(ReadTimeCalculator.minutes(html, SupportedLocale.EN))
      .isEqualTo(2);
  }

  @Test
  void japaneseDoesNotCountEntities() {
    String html = "あ&nbsp;".repeat(250) + "い&amp;".repeat(250);

    assertThat(ReadTimeCalculator.minutes(html, SupportedLocale.JA))
      .isEqualTo(1);
  }
}
