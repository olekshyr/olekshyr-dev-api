package dev.olekshyr.api.shared;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * springdoc's own advice for prod is springdoc.api-docs.enabled=false, which
 * removes its beans; the docs config must step aside instead of failing.
 */
class OpenApiConfigConditionTest {

  private final ApplicationContextRunner runner = new ApplicationContextRunner()
    .withUserConfiguration(OpenApiConfig.class);

  @Test
  void apiDocsOff_skipsTheDocsConfig() {
    runner
      .withPropertyValues("springdoc.api-docs.enabled=false")
      .run(context ->
        assertThat(context).hasNotFailed().doesNotHaveBean(GroupedOpenApi.class)
      );
  }
}
