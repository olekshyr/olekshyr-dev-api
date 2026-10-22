package dev.olekshyr.api.blog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.olekshyr.api.PostgresContainerConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** Admin tag API against a real Postgres, seeded from blog-seed.sql. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(PostgresContainerConfiguration.class)
@Sql("/blog-seed.sql")
class AdminTagApiIntegrationTest {

  private static final String TAGS = "/api/v1/admin/tags";

  @Autowired
  MockMvc mockMvc;

  @Autowired
  JdbcTemplate jdbc;

  private long idOf(String slug) {
    return jdbc.queryForObject(
      "SELECT id FROM tags WHERE slug = ?",
      Long.class,
      slug
    );
  }

  private ResultActions send(MockHttpServletRequestBuilder request)
    throws Exception {
    return mockMvc.perform(request.header("X-Api-Key", "test-admin-key"));
  }

  private ResultActions sendJson(
    MockHttpServletRequestBuilder request,
    String json
  ) throws Exception {
    return send(request.contentType(MediaType.APPLICATION_JSON).content(json));
  }

  @Test
  void listsEveryTagWithAllNames_sortedBySlug() throws Exception {
    send(get(TAGS))
      .andExpect(status().isOk())
      .andExpect(
        jsonPath("$[*].slug").value(contains("java", "life", "spring"))
      )
      .andExpect(jsonPath("$[0].id").value(idOf("java")))
      .andExpect(jsonPath("$[0].color").value("#b07219"))
      .andExpect(jsonPath("$[1].names.en").value("Life"))
      .andExpect(jsonPath("$[1].names.uk").value("Життя"))
      .andExpect(jsonPath("$[1].names.ja").value("生活"))
      .andExpect(jsonPath("$[2].names.ja").doesNotExist());
  }

  @Test
  void namesComeInCanonicalLocaleOrder() throws Exception {
    String body = send(get(TAGS + "/" + idOf("life")))
      .andReturn()
      .getResponse()
      .getContentAsString();

    assertThat(body)
      .contains("{\"en\":\"Life\",\"uk\":\"Життя\",\"ja\":\"生活\"}");
  }

  @Test
  void create_returnsLocation() throws Exception {
    MockHttpServletResponse response = sendJson(
      post(TAGS),
      """
      {"slug": "kotlin", "color": "#A97BFF", "names": {"en": "Kotlin", "ja": "コトリン"}}
      """
    )
      .andExpect(status().isCreated())
      .andExpect(jsonPath("$.slug").value("kotlin"))
      .andExpect(jsonPath("$.names.ja").value("コトリン"))
      .andReturn()
      .getResponse();

    send(get(response.getHeader("Location")))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.color").value("#A97BFF"));
  }

  @Test
  void createDuplicateSlug_returnsDuplicateSlug() throws Exception {
    sendJson(post(TAGS), "{\"slug\": \"java\", \"names\": {}}")
      .andExpect(status().isConflict())
      .andExpect(jsonPath("$.code").value("DUPLICATE_SLUG"));
  }

  @Test
  void duplicateNameInLocale_returnsConflict() throws Exception {
    sendJson(
      post(TAGS),
      "{\"slug\": \"java-2\", \"names\": {\"en\": \"Java\"}}"
    )
      .andExpect(status().isConflict())
      .andExpect(jsonPath("$.code").value("CONFLICT"));
  }

  @Test
  void invalidColor_returnsFieldDetails() throws Exception {
    sendJson(
      post(TAGS),
      "{\"slug\": \"go\", \"color\": \"red\", \"names\": {}}"
    )
      .andExpect(status().isBadRequest())
      .andExpect(jsonPath("$.details[0].field").value("color"))
      .andExpect(jsonPath("$.details[0].code").value("PATTERN"));
  }

  @Test
  void unsupportedLocaleKey_returnsValidationFailed() throws Exception {
    sendJson(post(TAGS), "{\"slug\": \"go\", \"names\": {\"fr\": \"Go\"}}")
      .andExpect(status().isBadRequest())
      .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
  }

  @Test
  void update_replacesSlugColorAndNames() throws Exception {
    sendJson(
      put(TAGS + "/" + idOf("spring")),
      "{\"slug\": \"spring-boot\", \"names\": {\"en\": \"Spring Boot\", \"ja\": \"スプリング\"}}"
    )
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.slug").value("spring-boot"))
      .andExpect(jsonPath("$.color").isEmpty())
      .andExpect(jsonPath("$.names.en").value("Spring Boot"))
      .andExpect(jsonPath("$.names.uk").doesNotExist())
      .andExpect(jsonPath("$.names.ja").value("スプリング"));
  }

  @Test
  void updateWithSameSlug_isAllowed() throws Exception {
    sendJson(
      put(TAGS + "/" + idOf("life")),
      "{\"slug\": \"life\", \"color\": \"#123456\", \"names\": {\"en\": \"Life\"}}"
    )
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.color").value("#123456"));
  }

  @Test
  void updateToTakenSlug_returnsDuplicateSlug() throws Exception {
    sendJson(
      put(TAGS + "/" + idOf("life")),
      "{\"slug\": \"java\", \"names\": {}}"
    )
      .andExpect(status().isConflict())
      .andExpect(jsonPath("$.code").value("DUPLICATE_SLUG"));
  }

  @Test
  void deleteUnusedTag() throws Exception {
    String location = sendJson(
      post(TAGS),
      "{\"slug\": \"unused\", \"names\": {}}"
    )
      .andReturn()
      .getResponse()
      .getHeader("Location");

    send(delete(location)).andExpect(status().isNoContent());
    send(get(location)).andExpect(status().isNotFound());
  }

  @Test
  void deleteTagInUse_returnsTagInUse() throws Exception {
    send(delete(TAGS + "/" + idOf("spring")))
      .andExpect(status().isConflict())
      .andExpect(jsonPath("$.code").value("TAG_IN_USE"));
  }

  @Test
  void tagUsedOnlyByDeletedPost_isStillInUse() throws Exception {
    jdbc.update(
      "DELETE FROM post_tags WHERE tag_id = ? AND post_id = " +
      "(SELECT id FROM posts WHERE slug = 'hello-world')",
      idOf("java")
    );

    send(delete(TAGS + "/" + idOf("java")))
      .andExpect(jsonPath("$.code").value("TAG_IN_USE"));
  }

  @Test
  void unknownId_returnsNotFound() throws Exception {
    send(get(TAGS + "/999999"))
      .andExpect(status().isNotFound())
      .andExpect(jsonPath("$.code").value("NOT_FOUND"));
  }
}
