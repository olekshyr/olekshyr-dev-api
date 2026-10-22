package dev.olekshyr.api.blog;

import dev.olekshyr.api.ClearCachesTestExecutionListener;
import dev.olekshyr.api.PostgresContainerConfiguration;
import dev.olekshyr.api.RedisContainerConfiguration;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestExecutionListeners;
import org.springframework.test.context.TestExecutionListeners.MergeMode;
import org.springframework.test.context.jdbc.Sql;

/**
 * Blog API test against real Postgres and Redis, seeded from blog-seed.sql.
 * Every cache is cleared before each test: the seed doesn't touch Redis, so
 * cached responses would otherwise leak from one test into the next.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@SpringBootTest
@AutoConfigureMockMvc
@Import(
  { PostgresContainerConfiguration.class, RedisContainerConfiguration.class }
)
@TestExecutionListeners(
  listeners = ClearCachesTestExecutionListener.class,
  mergeMode = MergeMode.MERGE_WITH_DEFAULTS
)
@Sql("/blog-seed.sql")
@interface BlogApiTest {
}
