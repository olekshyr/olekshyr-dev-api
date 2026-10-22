package dev.olekshyr.api.blog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.olekshyr.api.PostgresContainerConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;

/**
 * D25: with Redis unreachable (nothing listens on port 1), reads and writes
 * still work, straight from Postgres, and every failed cache call is logged.
 */
@SpringBootTest(
  properties = {
    "spring.data.redis.host=localhost",
    "spring.data.redis.port=1",
    // Fast and deterministic even where packets to port 1 are dropped
    "spring.data.redis.connect-timeout=500ms",
    "spring.data.redis.timeout=500ms",
  }
)
@AutoConfigureMockMvc
@Import(PostgresContainerConfiguration.class)
@ExtendWith(OutputCaptureExtension.class)
@Sql("/blog-seed.sql")
class PostCacheRedisDownIntegrationTest {

  private static final String KEY_HEADER = "X-Api-Key";

  @Autowired
  MockMvc mockMvc;

  @Autowired
  JdbcTemplate jdbc;

  @Test
  void publicReads_comeFromTheDatabase(CapturedOutput output) throws Exception {
    mockMvc
      .perform(get("/api/v1/posts").header(KEY_HEADER, "test-public-key"))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.content.length()").value(2));
    mockMvc
      .perform(
        get("/api/v1/posts/hello-world").header(KEY_HEADER, "test-public-key")
      )
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.title").value("Hello, world"));

    assertThat(output)
      .contains("Cache get failed for blog-post-list::en:0:10")
      .contains("Cache put failed for blog-post-list::en:0:10")
      .contains("Cache put failed for blog-post-detail::hello-world:en");
  }

  @Test
  void adminWrite_succeedsWithoutEvicting(CapturedOutput output)
    throws Exception {
    long id = jdbc.queryForObject(
      "SELECT id FROM posts WHERE slug = 'hello-world'",
      Long.class
    );

    mockMvc
      .perform(
        put("/api/v1/admin/posts/" + id + "/translations/en")
          .header(KEY_HEADER, "test-admin-key")
          .contentType(MediaType.APPLICATION_JSON)
          .content(
            """
            {"title": "Edited", "excerpt": "The first post.",
             "body": "<p>Hello.</p>", "metaTitle": null,
             "metaDescription": "The first post on the blog."}
            """
          )
      )
      .andExpect(status().isOk());
    assertThat(output)
      .contains("Cache clear failed for blog-post-list")
      .contains("Cache clear failed for blog-post-detail");

    mockMvc
      .perform(
        get("/api/v1/posts/hello-world").header(KEY_HEADER, "test-public-key")
      )
      .andExpect(jsonPath("$.title").value("Edited"));
  }
}
