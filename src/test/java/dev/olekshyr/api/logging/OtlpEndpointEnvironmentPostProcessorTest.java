package dev.olekshyr.api.logging;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.core.io.UrlResource;
import org.springframework.core.io.support.PropertiesLoaderUtils;
import org.springframework.core.io.support.SpringFactoriesLoader;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.util.StringUtils;

class OtlpEndpointEnvironmentPostProcessorTest {

  private static final String LOGGING_ENDPOINT =
    "management.opentelemetry.logging.export.otlp.endpoint";

  private final OtlpEndpointEnvironmentPostProcessor processor =
    new OtlpEndpointEnvironmentPostProcessor();

  private MockEnvironment process(MockEnvironment environment) {
    processor.postProcessEnvironment(environment, new SpringApplication());
    return environment;
  }

  @Test
  void setVariable_setsEndpoint() {
    MockEnvironment env = process(
      new MockEnvironment()
        .withProperty("OTLP_LOGGING_ENDPOINT", "http://collector:4318/v1/logs")
    );

    assertThat(env.getProperty(LOGGING_ENDPOINT))
      .isEqualTo("http://collector:4318/v1/logs");
  }

  @Test
  void unsetVariable_leavesEndpointUnset() {
    MockEnvironment env = process(new MockEnvironment());

    assertThat(env.containsProperty(LOGGING_ENDPOINT)).isFalse();
  }

  @Test
  void blankVariable_leavesEndpointUnset() {
    MockEnvironment env = process(
      new MockEnvironment().withProperty("OTLP_LOGGING_ENDPOINT", "  ")
    );

    assertThat(env.containsProperty(LOGGING_ENDPOINT)).isFalse();
  }

  @Test
  void mapsTracingAndMetricsToo() {
    MockEnvironment env = process(
      new MockEnvironment()
        .withProperty("OTLP_TRACING_ENDPOINT", "http://c/v1/traces")
        .withProperty("OTLP_METRICS_ENDPOINT", "http://c/v1/metrics")
    );

    assertThat(
      env.getProperty("management.opentelemetry.tracing.export.otlp.endpoint")
    )
      .isEqualTo("http://c/v1/traces");
    assertThat(env.getProperty("management.otlp.metrics.export.url"))
      .isEqualTo("http://c/v1/metrics");
  }

  @Test
  void isRegisteredWithSpringBoot() throws IOException {
    List<String> registered = new ArrayList<>();
    for (URL url : Collections.list(
      getClass()
        .getClassLoader()
        .getResources(SpringFactoriesLoader.FACTORIES_RESOURCE_LOCATION)
    )) {
      Properties factories = PropertiesLoaderUtils.loadProperties(
        new UrlResource(url)
      );
      String names = factories.getProperty(
        EnvironmentPostProcessor.class.getName(),
        ""
      );
      registered.addAll(
        Arrays.asList(StringUtils.commaDelimitedListToStringArray(names))
      );
    }

    assertThat(registered)
      .extracting(String::strip)
      .contains(OtlpEndpointEnvironmentPostProcessor.class.getName());
  }

  @Test
  void explicitBootProperty_winsOverVariable() {
    MockEnvironment env = process(
      new MockEnvironment()
        .withProperty("OTLP_LOGGING_ENDPOINT", "http://from-variable")
        .withProperty(LOGGING_ENDPOINT, "http://explicit")
    );

    assertThat(env.getProperty(LOGGING_ENDPOINT)).isEqualTo("http://explicit");
  }
}
