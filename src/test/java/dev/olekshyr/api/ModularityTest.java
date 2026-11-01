package dev.olekshyr.api;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

/**
 * Each top-level package is a Spring Modulith module. Fails on a dependency
 * cycle between modules (shared must never reach into blog) or on a reference
 * to another module's internal subpackage.
 */
class ModularityTest {

  @Test
  void moduleBoundariesHold() {
    ApplicationModules.of(ApiApplication.class).verify();
  }
}
