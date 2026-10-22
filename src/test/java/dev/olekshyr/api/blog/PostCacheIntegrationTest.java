package dev.olekshyr.api.blog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Public reads cached in a real Redis (Phase 4, D24-D26). A change made
 * straight in the database, bypassing the admin API, shows whether a response
 * came from the cache.
 */
@BlogApiTest
class PostCacheIntegrationTest {

  private static final String KEY_HEADER = "X-Api-Key";
  private static final String PUBLIC_KEY = "test-public-key";
  private static final String ADMIN_KEY = "test-admin-key";
  private static final String DETAIL_KEY = "blog-post-detail::hello-world:en";

  @Autowired
  MockMvc mockMvc;

  @Autowired
  JdbcTemplate jdbc;

  @Autowired
  StringRedisTemplate redis;

  private ResultActions publicGet(String path, String acceptLanguage)
    throws Exception {
    return mockMvc.perform(
      get(path)
        .header(KEY_HEADER, PUBLIC_KEY)
        .header("Accept-Language", acceptLanguage)
    );
  }

  private String body(String path, String acceptLanguage) throws Exception {
    return publicGet(path, acceptLanguage)
      .andExpect(status().isOk())
      .andReturn()
      .getResponse()
      .getContentAsString();
  }

  private ResultActions admin(MockHttpServletRequestBuilder request)
    throws Exception {
    return mockMvc.perform(request.header(KEY_HEADER, ADMIN_KEY));
  }

  private ResultActions adminJson(
    MockHttpServletRequestBuilder request,
    String json
  ) throws Exception {
    return admin(request.contentType(MediaType.APPLICATION_JSON).content(json));
  }

  private void retitleInDatabase(String slug, String locale, String title) {
    jdbc.update(
      """
      UPDATE post_translations SET title = ?
      WHERE locale = ? AND post_id = (SELECT id FROM posts WHERE slug = ?)
      """,
      title,
      locale,
      slug
    );
  }

  private long postId(String slug) {
    return jdbc.queryForObject(
      "SELECT id FROM posts WHERE slug = ?",
      Long.class,
      slug
    );
  }

  @Nested
  class ServedFromCache {

    @Test
    void detail_secondReadReturnsTheCachedResponse() throws Exception {
      String first = body("/api/v1/posts/hello-world", "en");
      retitleInDatabase("hello-world", "en", "Changed in the database");

      String second = body("/api/v1/posts/hello-world", "en");

      assertThat(second).isEqualTo(first).contains("Hello, world");
    }

    @Test
    void list_secondReadReturnsTheCachedResponse() throws Exception {
      String first = body("/api/v1/posts", "en");
      retitleInDatabase("hello-world", "en", "Changed in the database");

      String second = body("/api/v1/posts", "en");

      assertThat(second).isEqualTo(first).contains("Hello, world");
    }

    @Test
    void list_ignoredSortParameterSharesTheEntry() throws Exception {
      body("/api/v1/posts", "en");
      retitleInDatabase("hello-world", "en", "Changed in the database");

      publicGet("/api/v1/posts?sort=title,asc", "en")
        .andExpect(jsonPath("$.content[1].title").value("Hello, world"));
    }

    @Test
    void list_isCachedPerLocale() throws Exception {
      body("/api/v1/posts", "en");

      publicGet("/api/v1/posts", "uk")
        .andExpect(jsonPath("$.content[0].title").value("Привіт, світе"));
    }

    @Test
    void list_isCachedPerPageSize() throws Exception {
      body("/api/v1/posts?size=1", "en");

      publicGet("/api/v1/posts?size=10", "en")
        .andExpect(jsonPath("$.content.length()").value(2));
    }

    @Test
    void detail_isCachedPerLocale() throws Exception {
      body("/api/v1/posts/hello-world", "en");

      publicGet("/api/v1/posts/hello-world", "uk")
        .andExpect(jsonPath("$.title").value("Привіт, світе"));
    }

    @Test
    void emptyPagePastTheEnd_isNotCached() throws Exception {
      body("/api/v1/posts?page=5", "en");

      assertThat(redis.hasKey("blog-post-list::en:5:10")).isFalse();
      assertThat(redis.hasKey("blog-post-list::en:0:10")).isFalse();
      body("/api/v1/posts", "en");
      assertThat(redis.hasKey("blog-post-list::en:0:10")).isTrue();
    }

    @Test
    void notFound_isNotCached() throws Exception {
      publicGet("/api/v1/posts/draft-only", "en")
        .andExpect(status().isNotFound());
      jdbc.update(
        "UPDATE post_translations SET status = 'PUBLISHED', published_at = now() WHERE post_id = ?",
        postId("draft-only")
      );

      publicGet("/api/v1/posts/draft-only", "en").andExpect(status().isOk());
    }
  }

  @Nested
  class AdminWritesEvict {

    @Test
    void upsertTranslation_showsTheNewTitle() throws Exception {
      body("/api/v1/posts/hello-world", "en");

      adminJson(
        put(
          "/api/v1/admin/posts/" + postId("hello-world") + "/translations/en"
        ),
        """
        {"title": "Edited", "excerpt": "The first post.",
         "body": "<p>Hello.</p>", "metaTitle": null,
         "metaDescription": "The first post on the blog."}
        """
      )
        .andExpect(status().isOk());

      publicGet("/api/v1/posts/hello-world", "en")
        .andExpect(jsonPath("$.title").value("Edited"));
    }

    @Test
    void unpublish_removesThePostFromTheList() throws Exception {
      body("/api/v1/posts", "en");

      admin(
        post(
          "/api/v1/admin/posts/" +
          postId("second-post") +
          "/translations/en/unpublish"
        )
      )
        .andExpect(status().isOk());

      publicGet("/api/v1/posts", "en")
        .andExpect(jsonPath("$.content.length()").value(1));
    }

    @Test
    void tagUpdate_showsTheNewTagName() throws Exception {
      body("/api/v1/posts/hello-world", "en");
      long javaId = jdbc.queryForObject(
        "SELECT id FROM tags WHERE slug = 'java'",
        Long.class
      );

      adminJson(
        put("/api/v1/admin/tags/" + javaId),
        """
        {"slug": "java", "color": "#b07219", "names": {"en": "Java 25"}}
        """
      )
        .andExpect(status().isOk());

      publicGet("/api/v1/posts/hello-world", "en")
        .andExpect(jsonPath("$.tags[0].name").value("Java 25"));
    }

    @Test
    void adminReads_keepTheCache() throws Exception {
      body("/api/v1/posts/hello-world", "en");
      retitleInDatabase("hello-world", "en", "Changed in the database");

      admin(get("/api/v1/admin/posts")).andExpect(status().isOk());
      admin(get("/api/v1/admin/posts/" + postId("hello-world")))
        .andExpect(status().isOk());
      admin(get("/api/v1/admin/tags")).andExpect(status().isOk());

      publicGet("/api/v1/posts/hello-world", "en")
        .andExpect(jsonPath("$.title").value("Hello, world"));
    }

    @Test
    void failedWrite_keepsTheCache() throws Exception {
      body("/api/v1/posts/hello-world", "en");
      retitleInDatabase("hello-world", "en", "Changed in the database");

      admin(
        post(
          "/api/v1/admin/posts/" +
          postId("hello-world") +
          "/translations/ja/publish"
        )
      )
        .andExpect(status().isBadRequest());

      publicGet("/api/v1/posts/hello-world", "en")
        .andExpect(jsonPath("$.title").value("Hello, world"));
    }
  }

  /** D26: an entry in an old shape is a miss, replaced from Postgres. */
  @Nested
  class StaleShape {

    @Test
    void missingField_readsFromTheDatabaseAndReplacesTheEntry()
      throws Exception {
      redis
        .opsForValue()
        .set(DETAIL_KEY, "{\"slug\":\"hello-world\",\"title\":\"Stale\"}");

      publicGet("/api/v1/posts/hello-world", "en")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.title").value("Hello, world"));

      assertThat(redis.opsForValue().get(DETAIL_KEY))
        .contains("availableLocales")
        .doesNotContain("Stale");
    }

    @Test
    void unknownField_readsFromTheDatabaseAndReplacesTheEntry()
      throws Exception {
      body("/api/v1/posts/hello-world", "en");
      String cached = redis.opsForValue().get(DETAIL_KEY);
      assertThat(cached).contains("\"title\":\"Hello, world\"");
      redis
        .opsForValue()
        .set(
          DETAIL_KEY,
          cached
            .replace("\"title\":\"Hello, world\"", "\"title\":\"Stale\"")
            .replaceFirst("\\{", "{\"removedField\":1,")
        );

      publicGet("/api/v1/posts/hello-world", "en")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.title").value("Hello, world"));

      assertThat(redis.opsForValue().get(DETAIL_KEY))
        .doesNotContain("removedField")
        .doesNotContain("Stale");
    }
  }
}
