package dev.olekshyr.api.shared;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.ZoneOffset;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

class ClockConfigTest {

  private final Clock clock = new ClockConfig().clock();

  @Test
  void isUtc() {
    assertThat(clock.getZone()).isEqualTo(ZoneOffset.UTC);
  }

  /** Postgres keeps microseconds; anything finer would be lost on save. */
  @RepeatedTest(5)
  void ticksInMicroseconds() {
    assertThat(clock.instant().getNano() % 1_000).isZero();
  }
}
