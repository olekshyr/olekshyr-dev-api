package dev.olekshyr.api.shared;

import java.time.Clock;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The application's one source of time: every timestamp it writes (auditing,
 * publish, soft delete, error envelopes) comes from this bean, so tests can
 * replace it with a fixed or adjustable clock.
 */
@Configuration(proxyBeanMethods = false)
public class ClockConfig {

  /**
   * UTC, ticking in microseconds: the precision Postgres stores (timestamptz).
   * With nanoseconds, a write's response would show a different value than a
   * later read of the same row.
   */
  @Bean
  Clock clock() {
    return Clock.tick(Clock.systemUTC(), Duration.of(1, ChronoUnit.MICROS));
  }
}
