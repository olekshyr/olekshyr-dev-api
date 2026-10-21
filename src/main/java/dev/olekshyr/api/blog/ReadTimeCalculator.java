package dev.olekshyr.api.blog;

import dev.olekshyr.api.shared.SupportedLocale;
import java.util.regex.Pattern;

/** Read time from the body; never stored (ERD). */
final class ReadTimeCalculator {

  private static final int WORDS_PER_MINUTE = 200;
  /** Japanese has no spaces between words, so count characters instead. */
  private static final int JAPANESE_CHARS_PER_MINUTE = 500;

  private static final Pattern TAG = Pattern.compile("<[^>]*>");
  /** CKEditor puts &amp;nbsp; between words; entities count as a space. */
  private static final Pattern ENTITY = Pattern.compile(
    "&(?:#\\d+|#[xX][0-9a-fA-F]+|[a-zA-Z][a-zA-Z0-9]*);"
  );
  private static final Pattern WHITESPACE = Pattern.compile("\\s+");

  private ReadTimeCalculator() {}

  static int minutes(String html, SupportedLocale locale) {
    String withoutTags = TAG.matcher(html).replaceAll(" ");
    String text = ENTITY.matcher(withoutTags).replaceAll(" ").strip();
    if (text.isEmpty()) {
      return 1;
    }
    if (locale == SupportedLocale.JA) {
      String chars = WHITESPACE.matcher(text).replaceAll("");
      return ceilDiv(
        chars.codePointCount(0, chars.length()),
        JAPANESE_CHARS_PER_MINUTE
      );
    }
    return ceilDiv(WHITESPACE.split(text).length, WORDS_PER_MINUTE);
  }

  private static int ceilDiv(int count, int perMinute) {
    return Math.max(1, Math.ceilDiv(count, perMinute));
  }
}
