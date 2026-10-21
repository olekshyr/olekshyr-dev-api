package dev.olekshyr.api.shared;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Properties;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Every error code needs a message in every supported locale (D17). */
class ErrorCodeMessagesTest {

  @ParameterizedTest
  @ValueSource(
    strings = {
      "messages.properties", "messages_uk.properties", "messages_ja.properties",
    }
  )
  void everyErrorCodeHasAMessage(String file) throws IOException {
    Properties messages = load(file);

    assertThat(Arrays.stream(ErrorCode.values()).map(ErrorCode::messageKey))
      .allSatisfy(key -> assertThat(messages.getProperty(key)).isNotBlank());
  }

  private static Properties load(String file) throws IOException {
    Properties properties = new Properties();
    try (
      InputStream in = ErrorCodeMessagesTest.class.getResourceAsStream(
          "/" + file
        )
    ) {
      assertThat(in).as(file).isNotNull();
      properties.load(new InputStreamReader(in, StandardCharsets.UTF_8));
    }
    return properties;
  }
}
