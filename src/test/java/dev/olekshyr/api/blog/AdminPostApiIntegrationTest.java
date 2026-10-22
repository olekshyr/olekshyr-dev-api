package dev.olekshyr.api.blog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Collections;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.bean.override.convention.TestBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Admin post API against a real Postgres, seeded from blog-seed.sql. The
 * application clock is fixed, so server-set timestamps are exact.
 */
@BlogApiTest
class AdminPostApiIntegrationTest {

  private static final String KEY_HEADER = "X-Api-Key";
  private static final String ADMIN_KEY = "test-admin-key";
  private static final String PUBLIC_KEY = "test-public-key";
  private static final String POSTS = "/api/v1/admin/posts";

  private static final String COMPLETE_TRANSLATION =
    """
    {"title": "New post", "excerpt": "Short.", "body": "<p>Body</p>",
     "metaTitle": null, "metaDescription": "About the new post."}
    """;

  private static final String NOW = "2026-06-01T10:00:00Z";

  @TestBean
  Clock clock;

  static Clock clock() {
    return Clock.fixed(Instant.parse(NOW), ZoneOffset.UTC);
  }

  @Autowired
  MockMvc mockMvc;

  @Autowired
  JdbcTemplate jdbc;

  private long idOf(String slug) {
    return jdbc.queryForObject(
      "SELECT id FROM posts WHERE slug = ?",
      Long.class,
      slug
    );
  }

  private static MockHttpServletRequestBuilder admin(
    MockHttpServletRequestBuilder request
  ) {
    return request.header(KEY_HEADER, ADMIN_KEY);
  }

  private ResultActions send(MockHttpServletRequestBuilder request)
    throws Exception {
    return mockMvc.perform(admin(request));
  }

  private ResultActions sendJson(
    MockHttpServletRequestBuilder request,
    String json
  ) throws Exception {
    return send(request.contentType(MediaType.APPLICATION_JSON).content(json));
  }

  private ResultActions publicGet(String slug) throws Exception {
    return mockMvc.perform(
      get("/api/v1/posts/" + slug)
        .header(KEY_HEADER, PUBLIC_KEY)
        .header("Accept-Language", "en")
    );
  }

  @Nested
  class ListPosts {

    @Test
    void hidesDeletedPosts_newestUpdateFirst() throws Exception {
      jdbc.update(
        "UPDATE posts SET updated_at = '2030-01-01T00:00:00Z' WHERE slug = 'second-post'"
      );

      send(get(POSTS))
        .andExpect(status().isOk())
        .andExpect(header().string("Content-Language", "en"))
        .andExpect(jsonPath("$.page.totalElements").value(4))
        .andExpect(jsonPath("$.content[0].slug").value("second-post"))
        .andExpect(jsonPath("$.content[*].slug").value(hasItem("draft-only")))
        .andExpect(jsonPath("$.content[0].body").doesNotExist());
    }

    @Test
    void rowShowsEachTranslationsStatus() throws Exception {
      jdbc.update(
        "UPDATE posts SET updated_at = '2030-01-01T00:00:00Z' WHERE slug = 'hello-world'"
      );

      send(get(POSTS))
        .andExpect(jsonPath("$.content[0].id").value(idOf("hello-world")))
        .andExpect(jsonPath("$.content[0].deletedAt").isEmpty())
        .andExpect(
          jsonPath("$.content[0].translations[*].locale")
            .value(contains("en", "uk", "ja"))
        )
        .andExpect(
          jsonPath("$.content[0].translations[*].status")
            .value(contains("PUBLISHED", "PUBLISHED", "DRAFT"))
        )
        .andExpect(jsonPath("$.content[0].translations[0].updatedAt").exists());
    }

    @Test
    void deletedTrue_includesDeletedPosts() throws Exception {
      send(get(POSTS + "?deleted=true"))
        .andExpect(jsonPath("$.page.totalElements").value(5))
        .andExpect(
          jsonPath("$.content[?(@.slug == 'deleted-post')].deletedAt")
            .value(contains("2026-02-15T00:00:00Z"))
        );
    }

    @Test
    void paginates() throws Exception {
      send(get(POSTS + "?page=1&size=3"))
        .andExpect(jsonPath("$.content.length()").value(1))
        .andExpect(jsonPath("$.page.totalPages").value(2));
    }
  }

  @Nested
  class GetPost {

    @Test
    void returnsEveryTranslationAndTag() throws Exception {
      send(get(POSTS + "/" + idOf("hello-world")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.slug").value("hello-world"))
        .andExpect(jsonPath("$.createdAt").exists())
        .andExpect(
          jsonPath("$.translations[*].locale").value(contains("en", "uk", "ja"))
        )
        .andExpect(jsonPath("$.translations[0].metaTitle").isEmpty())
        .andExpect(jsonPath("$.translations[2].status").value("DRAFT"))
        .andExpect(
          jsonPath("$.translations[2].title").value("こんにちは、世界")
        )
        .andExpect(jsonPath("$.tags[*].slug").value(contains("java", "spring")))
        .andExpect(jsonPath("$.tags[1].names.en").value("Spring"))
        .andExpect(jsonPath("$.tags[1].names.ja").doesNotExist());
    }

    @Test
    void contentIgnoresAcceptLanguage() throws Exception {
      send(
        get(POSTS + "/" + idOf("hello-world")).header("Accept-Language", "ja")
      )
        .andExpect(header().string("Content-Language", "ja"))
        .andExpect(jsonPath("$.translations.length()").value(3));
    }

    @Test
    void deletedPost_isStillReturned() throws Exception {
      send(get(POSTS + "/" + idOf("deleted-post")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.deletedAt").value("2026-02-15T00:00:00Z"));
    }

    @Test
    void unknownId_returnsNotFound() throws Exception {
      send(get(POSTS + "/999999"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void nonNumericId_returnsValidationFailed() throws Exception {
      send(get(POSTS + "/hello-world"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }
  }

  @Test
  void fullLifecycle() throws Exception {
    String created = sendJson(post(POSTS), "{\"slug\": \"new-post\"}")
      .andExpect(status().isCreated())
      .andExpect(jsonPath("$.slug").value("new-post"))
      .andExpect(jsonPath("$.translations").isEmpty())
      .andReturn()
      .getResponse()
      .getContentAsString();
    long id = ((Number) JsonPath.read(created, "$.id")).longValue();

    sendJson(put(POSTS + "/" + id + "/translations/en"), COMPLETE_TRANSLATION)
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.translations[0].status").value("DRAFT"));
    publicGet("new-post").andExpect(status().isNotFound());

    String published = send(post(POSTS + "/" + id + "/translations/en/publish"))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.translations[0].status").value("PUBLISHED"))
      .andReturn()
      .getResponse()
      .getContentAsString();
    String publishedAt = JsonPath.read(
      published,
      "$.translations[0].publishedAt"
    );
    assertThat(publishedAt).isEqualTo(NOW);
    publicGet("new-post")
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.title").value("New post"));

    sendJson(put(POSTS + "/" + id), "{\"slug\": \"new-post\"}")
      .andExpect(status().isOk());
    sendJson(put(POSTS + "/" + id), "{\"slug\": \"renamed\"}")
      .andExpect(status().isConflict())
      .andExpect(jsonPath("$.code").value("SLUG_FROZEN"));

    send(post(POSTS + "/" + id + "/translations/en/unpublish"))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.translations[0].status").value("DRAFT"))
      .andExpect(jsonPath("$.translations[0].publishedAt").value(publishedAt));
    publicGet("new-post").andExpect(status().isNotFound());

    send(post(POSTS + "/" + id + "/translations/en/publish"))
      .andExpect(jsonPath("$.translations[0].publishedAt").value(publishedAt));

    send(delete(POSTS + "/" + id)).andExpect(status().isNoContent());
    publicGet("new-post").andExpect(status().isNotFound());
    sendJson(post(POSTS), "{\"slug\": \"new-post\"}")
      .andExpect(status().isConflict())
      .andExpect(jsonPath("$.code").value("DUPLICATE_SLUG"));
  }

  @Nested
  class CreateAndSlug {

    @Test
    void returnsLocationOfTheNewPost() throws Exception {
      MockHttpServletResponse response = sendJson(
        post(POSTS),
        "{\"slug\": \"fresh\"}"
      )
        .andExpect(status().isCreated())
        .andReturn()
        .getResponse();
      long id =
        (
          (Number) JsonPath.read(response.getContentAsString(), "$.id")
        ).longValue();

      assertThat(response.getHeader("Location")).endsWith(POSTS + "/" + id);
      send(get(response.getHeader("Location")))
        .andExpect(jsonPath("$.slug").value("fresh"));
    }

    @Test
    void duplicateSlug_isLocalized() throws Exception {
      sendJson(
        post(POSTS).header("Accept-Language", "uk"),
        "{\"slug\": \"hello-world\"}"
      )
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("DUPLICATE_SLUG"))
        .andExpect(jsonPath("$.message").value("Цей slug уже зайнятий."));
    }

    @Test
    void deletedPostsSlugStaysReserved() throws Exception {
      sendJson(post(POSTS), "{\"slug\": \"deleted-post\"}")
        .andExpect(jsonPath("$.code").value("DUPLICATE_SLUG"));
    }

    @Test
    void malformedSlug_returnsFieldDetails() throws Exception {
      sendJson(post(POSTS), "{\"slug\": \"Bad Slug\"}")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
        .andExpect(jsonPath("$.details[0].field").value("slug"))
        .andExpect(jsonPath("$.details[0].code").value("PATTERN"));
    }

    @Test
    void slugChangesFreelyBeforePublishing() throws Exception {
      sendJson(put(POSTS + "/" + idOf("draft-only")), "{\"slug\": \"renamed\"}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.slug").value("renamed"));
    }

    @Test
    void slugChangeToTakenSlug_returnsDuplicateSlug() throws Exception {
      sendJson(
        put(POSTS + "/" + idOf("draft-only")),
        "{\"slug\": \"second-post\"}"
      )
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("DUPLICATE_SLUG"));
    }
  }

  @Nested
  class Translations {

    @Test
    void bodyIsSanitized() throws Exception {
      sendJson(
        put(POSTS + "/" + idOf("draft-only") + "/translations/en"),
        """
        {"title": "T", "body": "<p>Hi</p><script>alert(1)</script>"}
        """
      )
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.translations[0].body").value("<p>Hi</p>"));
    }

    @Test
    void bodyEmptyAfterSanitizing_returnsFieldDetails() throws Exception {
      sendJson(
        put(POSTS + "/" + idOf("draft-only") + "/translations/en"),
        """
        {"title": "T", "body": "<script>alert(1)</script>"}
        """
      )
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
        .andExpect(jsonPath("$.details[0].field").value("body"))
        .andExpect(jsonPath("$.details[0].code").value("NOT_BLANK"));

      send(get(POSTS + "/" + idOf("draft-only")))
        .andExpect(
          jsonPath("$.translations[0].body").value("<p>Not ready.</p>")
        );
    }

    @Test
    void editToPublishedTranslation_goesLive() throws Exception {
      sendJson(
        put(POSTS + "/" + idOf("hello-world") + "/translations/en"),
        COMPLETE_TRANSLATION
      )
        .andExpect(status().isOk());

      publicGet("hello-world").andExpect(jsonPath("$.title").value("New post"));
    }

    @Test
    void missingTitle_returnsFieldDetails() throws Exception {
      sendJson(
        put(POSTS + "/" + idOf("draft-only") + "/translations/en"),
        "{\"body\": \"<p>x</p>\"}"
      )
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.details[0].field").value("title"))
        .andExpect(jsonPath("$.details[0].code").value("NOT_BLANK"));
    }

    @Test
    void publishWithoutExcerptAndMetaDescription_listsBothFields()
      throws Exception {
      send(
        post(POSTS + "/" + idOf("hello-world") + "/translations/ja/publish")
          .header("Accept-Language", "ja")
      )
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
        .andExpect(
          jsonPath("$.details[*].field")
            .value(contains("excerpt", "metaDescription"))
        )
        .andExpect(
          jsonPath("$.details[*].code")
            .value(contains("NOT_BLANK", "NOT_BLANK"))
        )
        .andExpect(
          jsonPath("$.details[0].message").value("空欄にはできません。")
        );
    }

    @Test
    void publishMissingTranslation_returnsNotFound() throws Exception {
      send(post(POSTS + "/" + idOf("draft-only") + "/translations/uk/publish"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void localeSegmentIsCaseInsensitive() throws Exception {
      send(
        post(POSTS + "/" + idOf("hello-world") + "/translations/EN/unpublish")
      )
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.translations[0].status").value("DRAFT"));
    }

    @Test
    void unsupportedLocaleSegment_returnsValidationFailed() throws Exception {
      send(post(POSTS + "/" + idOf("hello-world") + "/translations/fr/publish"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }
  }

  @Nested
  class Tags {

    @Test
    void replacesTheTagSet() throws Exception {
      sendJson(
        put(POSTS + "/" + idOf("hello-world") + "/tags"),
        "{\"tagSlugs\": [\"life\", \"java\"]}"
      )
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.tags[*].slug").value(contains("java", "life")));
    }

    @Test
    void emptyListRemovesEveryTag() throws Exception {
      sendJson(
        put(POSTS + "/" + idOf("hello-world") + "/tags"),
        "{\"tagSlugs\": []}"
      )
        .andExpect(jsonPath("$.tags").isEmpty());
    }

    @Test
    void moreThanFiftyTags_returnsFieldDetails() throws Exception {
      String slugs = String.join(",", Collections.nCopies(51, "\"java\""));

      sendJson(
        put(POSTS + "/" + idOf("hello-world") + "/tags"),
        "{\"tagSlugs\": [" + slugs + "]}"
      )
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.details[0].field").value("tagSlugs"))
        .andExpect(jsonPath("$.details[0].code").value("SIZE"));
    }

    @Test
    void unknownTags_reportedByIndex() throws Exception {
      sendJson(
        put(POSTS + "/" + idOf("hello-world") + "/tags"),
        "{\"tagSlugs\": [\"life\", \"nope\", \"java\", \"nada\"]}"
      )
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
        .andExpect(
          jsonPath("$.details[*].field")
            .value(contains("tagSlugs[1]", "tagSlugs[3]"))
        )
        .andExpect(jsonPath("$.details[0].code").value("UNKNOWN_TAG"));

      send(get(POSTS + "/" + idOf("hello-world")))
        .andExpect(
          jsonPath("$.tags[*].slug").value(contains("java", "spring"))
        );
    }
  }

  @Nested
  class DeletedPost {

    @Test
    void writesReturnNotFound() throws Exception {
      long id = idOf("deleted-post");

      sendJson(put(POSTS + "/" + id), "{\"slug\": \"deleted-post\"}")
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("NOT_FOUND"));
      sendJson(put(POSTS + "/" + id + "/translations/en"), COMPLETE_TRANSLATION)
        .andExpect(status().isNotFound());
      send(post(POSTS + "/" + id + "/translations/en/unpublish"))
        .andExpect(status().isNotFound());
      sendJson(put(POSTS + "/" + id + "/tags"), "{\"tagSlugs\": []}")
        .andExpect(status().isNotFound());
    }

    @Test
    void deletingAgainKeepsDeletedAt() throws Exception {
      long id = idOf("deleted-post");

      send(delete(POSTS + "/" + id)).andExpect(status().isNoContent());

      send(get(POSTS + "/" + id))
        .andExpect(jsonPath("$.deletedAt").value("2026-02-15T00:00:00Z"));
    }

    @Test
    void deleteUnknownId_returnsNotFound() throws Exception {
      send(delete(POSTS + "/999999")).andExpect(status().isNotFound());
    }
  }

  @Nested
  class Security {

    @Test
    void missingKey_returns403() throws Exception {
      mockMvc.perform(get(POSTS)).andExpect(status().isForbidden());
    }

    @Test
    void publicKey_returns403() throws Exception {
      mockMvc
        .perform(get(POSTS).header(KEY_HEADER, PUBLIC_KEY))
        .andExpect(status().isForbidden());
    }
  }

  @Test
  void serverTimestampsComeFromTheApplicationClock() throws Exception {
    sendJson(post(POSTS), "{\"slug\": \"stamped\"}")
      .andExpect(jsonPath("$.createdAt").value(NOW))
      .andExpect(jsonPath("$.updatedAt").value(NOW));

    send(delete(POSTS + "/" + idOf("stamped")));
    send(get(POSTS + "/" + idOf("stamped")))
      .andExpect(jsonPath("$.deletedAt").value(NOW));
  }
}
