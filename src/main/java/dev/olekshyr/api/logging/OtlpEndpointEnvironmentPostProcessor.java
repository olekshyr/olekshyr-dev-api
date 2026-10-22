package dev.olekshyr.api.logging;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.config.ConfigDataEnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.util.ClassUtils;

/**
 * Maps the OTLP_* variables onto Spring Boot's exporter endpoints, but only
 * when they are set. An unset variable leaves the endpoint undefined, which
 * Boot treats as "no exporter"; an empty endpoint would fail at startup.
 * Explicit management.* properties still win, and Docker Compose's Grafana
 * connection still applies locally.
 *
 * <p>Metrics are the exception: Boot defaults their endpoint to localhost, so
 * without OTLP_METRICS_ENDPOINT a deployed app would fail a push every minute.
 * Export is turned off then, unless Docker Compose support is on the classpath
 * (local runs only: Boot leaves it out of the packaged jar), where Compose's
 * Grafana connection supplies the endpoint.
 */
public class OtlpEndpointEnvironmentPostProcessor
  implements EnvironmentPostProcessor, Ordered {

  private static final String METRICS_URL =
    "management.otlp.metrics.export.url";
  private static final String METRICS_EXPORT_ENABLED =
    "management.otlp.metrics.export.enabled";
  private static final String DOCKER_COMPOSE_CLASS =
    "org.springframework.boot.docker.compose.core.DockerCompose";

  private static final Map<String, String> ENDPOINTS = Map.of(
    "OTLP_TRACING_ENDPOINT",
    "management.opentelemetry.tracing.export.otlp.endpoint",
    "OTLP_LOGGING_ENDPOINT",
    "management.opentelemetry.logging.export.otlp.endpoint",
    "OTLP_METRICS_ENDPOINT",
    METRICS_URL
  );

  private final boolean dockerComposePresent;

  public OtlpEndpointEnvironmentPostProcessor() {
    this(
      ClassUtils.isPresent(
        DOCKER_COMPOSE_CLASS,
        OtlpEndpointEnvironmentPostProcessor.class.getClassLoader()
      )
    );
  }

  OtlpEndpointEnvironmentPostProcessor(boolean dockerComposePresent) {
    this.dockerComposePresent = dockerComposePresent;
  }

  @Override
  public void postProcessEnvironment(
    ConfigurableEnvironment environment,
    SpringApplication application
  ) {
    Map<String, Object> endpoints = new LinkedHashMap<>();
    ENDPOINTS.forEach((variable, property) -> {
      String value = environment.getProperty(variable);
      if (value != null && !value.isBlank()) {
        endpoints.put(property, value.strip());
      }
    });
    if (!endpoints.containsKey(METRICS_URL) && !dockerComposePresent) {
      endpoints.put(METRICS_EXPORT_ENABLED, "false");
    }
    if (!endpoints.isEmpty()) {
      environment
        .getPropertySources()
        .addLast(new MapPropertySource("otlpEndpoints", endpoints));
    }
  }

  /** After config data, so OTLP_* values from the imported .env are visible. */
  @Override
  public int getOrder() {
    return ConfigDataEnvironmentPostProcessor.ORDER + 1;
  }
}
