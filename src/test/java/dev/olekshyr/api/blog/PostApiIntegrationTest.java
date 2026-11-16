package dev.olekshyr.api.blog;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** Public read API against a real Postgres, seeded from blog-seed.sql. */
@BlogApiTest
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
    void translatedItems_nameTheirLocaleAndAlternates() throws Exception {
      mockMvc
        .perform(publicGet("/api/v1/posts", "en"))
        .andExpect(jsonPath("$.content[1].slug").value("hello-world"))
        .andExpect(jsonPath("$.content[1].translated").value(true))
        .andExpect(jsonPath("$.content[1].locale").value("en"))
        .andExpect(
          jsonPath("$.content[1].availableLocales").value(contains("en", "uk"))
        );
    }

    @Test
    void oldestFirst() throws Exception {
      mockMvc
        .perform(publicGet("/api/v1/posts?sort=publishedAt,asc", "en"))
        .andExpect(status().isOk())
        .andExpect(
          jsonPath("$.content[*].slug")
            .value(contains("hello-world", "second-post"))
        );
    }

    @Test
    void newestFirst_isTheDefaultAndExplicit() throws Exception {
      mockMvc
        .perform(publicGet("/api/v1/posts?sort=publishedAt,desc", "en"))
        .andExpect(
          jsonPath("$.content[*].slug")
            .value(contains("second-post", "hello-world"))
        );
    }

    @Test
    void unsupportedSort_returnsValidationFailedNamingIt() throws Exception {
      for (String sort : new String[] { "title,asc", "publishedAt", "x" }) {
        mockMvc
          .perform(publicGet("/api/v1/posts?sort=" + sort, "en"))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
          .andExpect(jsonPath("$.details[0].field").value("sort"))
          .andExpect(jsonPath("$.details[0].code").value("INVALID_VALUE"));
      }
    }

    @Test
    void unsupportedInclude_returnsValidationFailedNamingIt() throws Exception {
      mockMvc
        .perform(publicGet("/api/v1/posts?include=drafts", "uk"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
        .andExpect(jsonPath("$.details[0].field").value("include"))
        .andExpect(jsonPath("$.details[0].code").value("INVALID_VALUE"))
        .andExpect(
          jsonPath("$.details[0].message").value("Недопустиме значення.")
        );
    }

    @Test
    void tagFilter() throws Exception {
      mockMvc
        .perform(publicGet("/api/v1/posts?tag=java", "en"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[*].slug").value(contains("hello-world")))
        .andExpect(jsonPath("$.page.totalElements").value(1));
    }

    @Test
    void unknownTag_isAnEmptyPage() throws Exception {
      mockMvc
        .perform(publicGet("/api/v1/posts?tag=no-such-tag", "en"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content").isEmpty())
        .andExpect(jsonPath("$.page.totalElements").value(0));
    }

    @Test
    void tagOfADeletedPostOnly_isAnEmptyPage() throws Exception {
      jdbc.update(
        "DELETE FROM post_tags WHERE post_id = (SELECT id FROM posts WHERE slug = 'hello-world')"
      );

      mockMvc
        .perform(publicGet("/api/v1/posts?tag=java&include=untranslated", "en"))
        .andExpect(jsonPath("$.content").isEmpty());
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

  /** include=untranslated (D28): every visible post, same pages everywhere. */
  @Nested
  class ListIncludingUntranslated {

    private static final String PATH = "/api/v1/posts?include=untranslated";

    @Test
    void listsEveryVisiblePost_byEarliestPublishInAnyLocale() throws Exception {
      mockMvc
        .perform(publicGet(PATH, "uk"))
        .andExpect(status().isOk())
        .andExpect(
          jsonPath("$.content[*].slug")
            .value(contains("japanese-only", "second-post", "hello-world"))
        )
        .andExpect(jsonPath("$.page.totalElements").value(3));
    }

    @Test
    void sameOrderAndCountInEveryLocale() throws Exception {
      for (String locale : new String[] { "en", "uk", "ja" }) {
        mockMvc
          .perform(publicGet(PATH + "&sort=publishedAt,asc", locale))
          .andExpect(
            jsonPath("$.content[*].slug")
              .value(contains("hello-world", "second-post", "japanese-only"))
          )
          .andExpect(jsonPath("$.page.totalElements").value(3));
      }
    }

    @Test
    void translatedItem_isInTheRequestedLocale() throws Exception {
      mockMvc
        .perform(publicGet(PATH, "uk"))
        .andExpect(jsonPath("$.content[2].translated").value(true))
        .andExpect(jsonPath("$.content[2].locale").value("uk"))
        .andExpect(jsonPath("$.content[2].title").value("Привіт, світе"))
        .andExpect(
          jsonPath("$.content[2].publishedAt").value("2026-01-12T09:00:00Z")
        );
    }

    @Test
    void untranslatedItem_isInItsFirstLocale_tagNamesInTheRequestedOne()
      throws Exception {
      mockMvc
        .perform(publicGet(PATH, "uk"))
        .andExpect(jsonPath("$.content[1].slug").value("second-post"))
        .andExpect(jsonPath("$.content[1].translated").value(false))
        .andExpect(jsonPath("$.content[1].locale").value("en"))
        .andExpect(jsonPath("$.content[1].title").value("Second post"))
        .andExpect(jsonPath("$.content[1].excerpt").value("Another one."))
        .andExpect(
          jsonPath("$.content[1].publishedAt").value("2026-02-01T09:00:00Z")
        )
        .andExpect(
          jsonPath("$.content[1].availableLocales").value(contains("en"))
        )
        .andExpect(jsonPath("$.content[1].tags[0].name").value("Життя"));
    }

    @Test
    void neverListsDraftsOrDeletedPosts() throws Exception {
      mockMvc
        .perform(publicGet(PATH + "&size=50", "en"))
        .andExpect(
          jsonPath("$.content[*].slug")
            .value(contains("japanese-only", "second-post", "hello-world"))
        );
    }

    @Test
    void combinesWithTagAndSort() throws Exception {
      mockMvc
        .perform(publicGet(PATH + "&tag=spring&sort=publishedAt,asc", "en"))
        .andExpect(
          jsonPath("$.content[*].slug")
            .value(contains("hello-world", "japanese-only"))
        )
        .andExpect(jsonPath("$.content[1].translated").value(false))
        .andExpect(jsonPath("$.page.totalElements").value(2));
    }

    @Test
    void paginates() throws Exception {
      mockMvc
        .perform(publicGet(PATH + "&page=1&size=2", "ja"))
        .andExpect(jsonPath("$.content[*].slug").value(contains("hello-world")))
        .andExpect(jsonPath("$.page.totalPages").value(2));
    }
  }

  /** The public tag list: what the post list's filter chips need (D29). */
  @Nested
  class ListTags {

    @Test
    void tagsOfPostsVisibleInTheLocale_withCounts() throws Exception {
      mockMvc
        .perform(publicGet("/api/v1/tags", "uk"))
        .andExpect(status().isOk())
        .andExpect(header().string("Content-Language", "uk"))
        .andExpect(jsonPath("$[*].slug").value(contains("java", "spring")))
        .andExpect(jsonPath("$[0].name").value("Java"))
        .andExpect(jsonPath("$[0].color").value("#b07219"))
        .andExpect(jsonPath("$[0].postCount").value(1))
        .andExpect(jsonPath("$[1].postCount").value(1));
    }

    @Test
    void includeUntranslated_countsEveryVisiblePost() throws Exception {
      mockMvc
        .perform(publicGet("/api/v1/tags?include=untranslated", "uk"))
        .andExpect(
          jsonPath("$[*].slug").value(contains("java", "life", "spring"))
        )
        .andExpect(jsonPath("$[1].name").value("Життя"))
        .andExpect(jsonPath("$[1].color").value(nullValue()))
        .andExpect(jsonPath("$[2].postCount").value(2));
    }

    @Test
    void tagWithoutNameInLocale_usesSlugAsName() throws Exception {
      mockMvc
        .perform(publicGet("/api/v1/tags", "ja"))
        .andExpect(jsonPath("$[*].slug").value(contains("spring")))
        .andExpect(jsonPath("$[0].name").value("spring"));
    }

    @Test
    void countsMatchTheFilteredList() throws Exception {
      mockMvc
        .perform(publicGet("/api/v1/tags?include=untranslated", "en"))
        .andExpect(jsonPath("$[2].slug").value("spring"))
        .andExpect(jsonPath("$[2].postCount").value(2));
      mockMvc
        .perform(
          publicGet("/api/v1/posts?include=untranslated&tag=spring", "en")
        )
        .andExpect(jsonPath("$.page.totalElements").value(2));
    }

    @Test
    void noVisiblePosts_isEmpty() throws Exception {
      jdbc.update("UPDATE posts SET deleted_at = now()");

      mockMvc
        .perform(publicGet("/api/v1/tags?include=untranslated", "en"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void unsupportedInclude_returnsValidationFailed() throws Exception {
      mockMvc
        .perform(publicGet("/api/v1/tags?include=all", "en"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.details[0].field").value("include"));
    }
  }

  @Nested
  class GetPost {

    @Test
    void neighbours_inTheLocale() throws Exception {
      mockMvc
        .perform(publicGet("/api/v1/posts/hello-world", "en"))
        .andExpect(jsonPath("$.previous").value(nullValue()))
        .andExpect(jsonPath("$.next.slug").value("second-post"))
        .andExpect(jsonPath("$.next.title").value("Second post"));
      mockMvc
        .perform(publicGet("/api/v1/posts/second-post", "en"))
        .andExpect(jsonPath("$.previous.slug").value("hello-world"))
        .andExpect(jsonPath("$.previous.title").value("Hello, world"))
        .andExpect(jsonPath("$.next").value(nullValue()));
    }

    @Test
    void neighbours_skipPostsNotPublishedInTheLocale() throws Exception {
      mockMvc
        .perform(publicGet("/api/v1/posts/hello-world", "uk"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.previous").value(nullValue()))
        .andExpect(jsonPath("$.next").value(nullValue()));
    }

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
        .andExpect(header().stringValues("Vary", hasItem("Accept-Language")))
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
  void missingKey_returns401() throws Exception {
    mockMvc.perform(get("/api/v1/posts")).andExpect(status().isUnauthorized());
  }
}
