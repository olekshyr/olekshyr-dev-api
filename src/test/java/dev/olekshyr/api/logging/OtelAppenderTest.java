package dev.olekshyr.api.logging;

import static org.assertj.core.api.Assertions.assertThatCode;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import io.opentelemetry.instrumentation.logback.appender.v1_0.OpenTelemetryAppender;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.logs.SdkLoggerProvider;
import org.junit.jupiter.api.Test;

/**
 * The logback appender must be built on the OpenTelemetry API version Boot
 * manages. A newer one compiles and starts fine but throws NoSuchMethodError on
 * the first log call that carries an exception, failing that request. This
 * fails the build instead when the appender or Boot is upgraded out of step.
 */
class OtelAppenderTest {

  @Test
  void logsAnExceptionThroughOpenTelemetry() {
    try (
      OpenTelemetrySdk openTelemetry = OpenTelemetrySdk
        .builder()
        .setLoggerProvider(SdkLoggerProvider.builder().build())
        .build()
    ) {
      LoggerContext context = new LoggerContext();
      OpenTelemetryAppender appender = new OpenTelemetryAppender();
      appender.setContext(context);
      appender.setOpenTelemetry(openTelemetry);
      appender.start();
      Logger logger = context.getLogger(OtelAppenderTest.class);
      logger.addAppender(appender);

      assertThatCode(() ->
          logger.error("Request failed", new IllegalStateException("boom"))
        )
        .doesNotThrowAnyException();
    }
  }
}
