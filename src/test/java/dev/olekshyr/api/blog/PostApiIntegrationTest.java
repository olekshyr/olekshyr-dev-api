package dev.olekshyr.api.blog;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.olekshyr.api.PostgresContainerConfiguration;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** Public read API against a real Postgres, seeded from blog-seed.sql. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(PostgresContainerConfiguration.class)
@Sql("/blog-seed.sql")
class PostApiIntegrationTest {

  private static final String KEY_HEADER = "X-Api-Key";
  private static final String PUBLIC_KEY = "test-public-key";

  @Autowired
  MockMvc mockMvc;

  @Autowired
  JdbcTemplate jdbc;

  private static MockHttpServletRequestBuilder publicGet(
    String path,
    String acceptLanguage
  ) {
    MockHttpServletRequestBuilder request = get(path)
      .header(KEY_HEADER, PUBLIC_KEY);
    return acceptLanguage == null
      ? request
      : request.header("Accept-Language", acceptLanguage);
  }

  @Nested
  class ListPosts {

    @Test
    void visibleInEnglish_newestFirst_withDefaultPageSize() throws Exception {
      mockMvc
        .perform(publicGet("/api/v1/posts", null))
        .andExpect(status().isOk())
        .andExpect(header().string("Content-Language", "en"))
        .andExpect(header().stringValues("Vary", hasItem("Accept-Language")))
        .andExpect(
          jsonPath("$.content[*].slug")
            .value(contains("second-post", "hello-world"))
        )
        .andExpect(jsonPath("$.page.number").value(0))
        .andExpect(jsonPath("$.page.size").value(10))
        .andExpect(jsonPath("$.page.totalElements").value(2))
        .andExpect(jsonPath("$.page.totalPages").value(1));
    }

    @Test
    void summaryFields() throws Exception {
      mockMvc
        .perform(publicGet("/api/v1/posts", "en"))
        .andExpect(jsonPath("$.content[1].title").value("Hello, world"))
        .andExpect(jsonPath("$.content[1].excerpt").value("The first post."))
        .andExpect(
          jsonPath("$.content[1].publishedAt").value("2026-01-10T09:00:00Z")
        )
        .andExpect(jsonPath("$.content[1].readTimeMinutes").value(1))
        .andExpect(jsonPath("$.content[1].body").doesNotExist())
        .andExpect(
          jsonPath("$.content[1].tags[*].slug")
            .value(contains("java", "spring"))
        )
        .andExpect(jsonPath("$.content[1].tags[0].name").value("Java"))
        .andExpect(jsonPath("$.content[1].tags[0].color").value("#b07219"));
    }

    @Test
    void localeComesFromAcceptLanguage() throws Exception {
      mockMvc
        .perform(publicGet("/api/v1/posts", "uk-UA"))
        .andExpect(header().string("Content-Language", "uk"))
        .andExpect(jsonPath("$.content[*].slug").value(contains("hello-world")))
        .andExpect(jsonPath("$.content[0].title").value("Привіт, світе"));
    }

    @Test
    void unsupportedLanguage_fallsBackToEnglish() throws Exception {
      mockMvc
        .perform(publicGet("/api/v1/posts", "fr"))
        .andExpect(header().string("Content-Language", "en"))
        .andExpect(jsonPath("$.page.totalElements").value(2));
    }

    @Test
    void tagWithoutNameInLocale_usesSlugAsName() throws Exception {
      mockMvc
        .perform(publicGet("/api/v1/posts", "ja"))
        .andExpect(
          jsonPath("$.content[*].slug").value(contains("japanese-only"))
        )
        .andExpect(jsonPath("$.content[0].tags[0].slug").value("spring"))
        .andExpect(jsonPath("$.content[0].tags[0].name").value("spring"));
    }

    @Test
    void paginates() throws Exception {
      mockMvc
        .perform(publicGet("/api/v1/posts?page=1&size=1", "en"))
        .andExpect(jsonPath("$.content[*].slug").value(contains("hello-world")))
        .andExpect(jsonPath("$.page.number").value(1))
        .andExpect(jsonPath("$.page.size").value(1))
        .andExpect(jsonPath("$.page.totalPages").value(2));
    }

    @Test
    void sizeIsCappedAtFifty() throws Exception {
      mockMvc
        .perform(publicGet("/api/v1/posts?size=999", "en"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.page.size").value(50));
    }

    @Test
    void garbagePaging_fallsBackToDefaults() throws Exception {
      mockMvc
        .perform(publicGet("/api/v1/posts?page=-3&size=abc", "en"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.page.number").value(0))
        .andExpect(jsonPath("$.page.size").value(10));
    }

    @Test
    void pagePastTheEnd_isEmpty() throws Exception {
      mockMvc
        .perform(publicGet("/api/v1/posts?page=5", "en"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content").isEmpty())
        .andExpect(jsonPath("$.page.totalElements").value(2));
    }

    @Test
    void noVisiblePosts_isEmptyPage() throws Exception {
      jdbc.update("UPDATE posts SET deleted_at = now()");

      mockMvc
        .perform(publicGet("/api/v1/posts", "en"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content").isEmpty())
        .andExpect(jsonPath("$.page.totalElements").value(0));
    }
  }

  @Nested
  class GetPost {

    @Test
    void detailFields() throws Exception {
      mockMvc
        .perform(publicGet("/api/v1/posts/hello-world", "en"))
        .andExpect(status().isOk())
        .andExpect(header().string("Content-Language", "en"))
        .andExpect(jsonPath("$.slug").value("hello-world"))
        .andExpect(jsonPath("$.title").value("Hello, world"))
        .andExpect(jsonPath("$.excerpt").value("The first post."))
        .andExpect(
          jsonPath("$.body").value("<p>Hello <strong>world</strong>.</p>")
        )
        .andExpect(
          jsonPath("$.metaDescription").value("The first post on the blog.")
        )
        .andExpect(jsonPath("$.publishedAt").value("2026-01-10T09:00:00Z"))
        .andExpect(jsonPath("$.updatedAt").exists())
        .andExpect(jsonPath("$.readTimeMinutes").value(1))
        .andExpect(jsonPath("$.tags[*].slug").value(contains("java", "spring")))
        .andExpect(jsonPath("$.availableLocales").value(contains("en", "uk")));
    }

    @Test
    void emptyMetaTitle_fallsBackToTitle() throws Exception {
      mockMvc
        .perform(publicGet("/api/v1/posts/hello-world", "en"))
        .andExpect(jsonPath("$.metaTitle").value("Hello, world"));
    }

    @Test
    void metaTitle_whenSet() throws Exception {
      mockMvc
        .perform(publicGet("/api/v1/posts/hello-world", "uk"))
        .andExpect(jsonPath("$.title").value("Привіт, світе"))
        .andExpect(jsonPath("$.metaTitle").value("Привіт, світе | Блог"));
    }

    @Test
    void availableLocales_useCanonicalOrder() throws Exception {
      jdbc.update(
        "UPDATE post_translations SET status = 'PUBLISHED', excerpt = 'x', " +
        "meta_description = 'x', published_at = now() WHERE locale = 'ja'"
      );

      mockMvc
        .perform(publicGet("/api/v1/posts/hello-world", "en"))
        .andExpect(
          jsonPath("$.availableLocales").value(contains("en", "uk", "ja"))
        );
    }

    @Test
    void notPublishedInLocale_returnsTranslationNotAvailable()
      throws Exception {
      mockMvc
        .perform(publicGet("/api/v1/posts/hello-world", "ja"))
        .andExpect(status().isNotFound())
        .andExpect(header().string("Content-Language", "ja"))
        .andExpect(jsonPath("$.code").value("TRANSLATION_NOT_AVAILABLE"))
        .andExpect(
          jsonPath("$.message")
            .value("この記事はリクエストされた言語では公開されていません。")
        )
        .andExpect(jsonPath("$.details").doesNotExist());
    }

    @Test
    void unknownSlug_returnsNotFound() throws Exception {
      expectNotFound("/api/v1/posts/no-such-post");
    }

    @Test
    void slugMatchIsExact() throws Exception {
      expectNotFound("/api/v1/posts/Hello-World");
    }

    @Test
    void draftOnlyPost_returnsNotFound() throws Exception {
      expectNotFound("/api/v1/posts/draft-only");
    }

    @Test
    void deletedPost_returnsNotFound() throws Exception {
      expectNotFound("/api/v1/posts/deleted-post");
    }

    private void expectNotFound(String path) throws Exception {
      mockMvc
        .perform(publicGet(path, "en"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }
  }

  @Test
  void writeMethod_returnsUnsupportedRequest() throws Exception {
    mockMvc
      .perform(post("/api/v1/posts").header(KEY_HEADER, PUBLIC_KEY))
      .andExpect(status().isMethodNotAllowed())
      .andExpect(jsonPath("$.code").value("UNSUPPORTED_REQUEST"));
  }

  @Test
  void openApi_documentsPageAndSizeAsQueryParams() throws Exception {
    mockMvc
      .perform(get("/v3/api-docs").header(KEY_HEADER, PUBLIC_KEY))
      .andExpect(status().isOk())
      .andExpect(
        jsonPath("$.paths['/api/v1/posts'].get.parameters[*].name")
          .value(containsInAnyOrder("page", "size"))
      );
  }

  @Test
  void missingKey_returns401() throws Exception {
    mockMvc.perform(get("/api/v1/posts")).andExpect(status().isUnauthorized());
  }
}
