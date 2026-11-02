package dev.olekshyr.api.shared;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Validation prints a locale map key as its enum name; details use the code. */
class LocaleFieldNameTest {

  @ParameterizedTest
  @CsvSource(
    {
      "names[EN], names[en]",
      "names[UK], names[uk]",
      "names[JA], names[ja]",
      "tagSlugs[1], tagSlugs[1]",
      "slug, slug",
      "names[FR], names[FR]",
    }
  )
  void localeKeysBecomeCodes(String field, String expected) {
    assertThat(GlobalExceptionHandler.withLocaleCodes(field))
      .isEqualTo(expected);
  }
}
