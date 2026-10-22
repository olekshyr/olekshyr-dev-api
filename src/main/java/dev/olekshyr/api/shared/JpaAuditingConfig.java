package dev.olekshyr.api.shared;

import java.time.Clock;
import java.util.Optional;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.auditing.DateTimeProvider;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@Configuration(proxyBeanMethods = false)
@EnableJpaAuditing(dateTimeProviderRef = "auditingDateTimeProvider")
@Import(ClockConfig.class)
public class JpaAuditingConfig {

  /** createdAt/updatedAt come from the application clock too. */
  @Bean
  DateTimeProvider auditingDateTimeProvider(Clock clock) {
    return () -> Optional.of(clock.instant());
  }
}
