package dev.olekshyr.api.blog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import dev.olekshyr.api.shared.ErrorCode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The OpenAPI spec is the frontend's integration reference (6.1): one
 * document per API-key tier, every error code documented, nullability exact.
 */
@BlogApiTest
class OpenApiDocsTest {

  private static final String KEY_HEADER = "X-Api-Key";
  private static final String PUBLIC_KEY = "test-public-key";

  @Autowired
  MockMvc mockMvc;

  private String spec(String group) throws Exception {
    return mockMvc
      .perform(get("/v3/api-docs/" + group).header(KEY_HEADER, PUBLIC_KEY))
      .andExpect(status().isOk())
      .andReturn()
      .getResponse()
      .getContentAsString();
  }

  private static List<Map<String, Object>> operations(String spec) {
    return JsonPath.read(spec, "$.paths.*.*");
  }

  @Test
  void eachGroup_holdsOnlyItsTiersPaths() throws Exception {
    List<String> publicPaths = new ArrayList<>(
      JsonPath.<Map<String, Object>>read(spec("public"), "$.paths").keySet()
    );
    List<String> adminPaths = new ArrayList<>(
      JsonPath.<Map<String, Object>>read(spec("admin"), "$.paths").keySet()
    );

    assertThat(publicPaths)
      .containsExactlyInAnyOrder("/api/v1/posts", "/api/v1/posts/{slug}");
    assertThat(adminPaths)
      .isNotEmpty()
      .allMatch(path -> path.startsWith("/api/v1/admin/"));
  }

  @Test
  void ungroupedDocument_isOff() throws Exception {
    mockMvc
      .perform(get("/v3/api-docs").header(KEY_HEADER, PUBLIC_KEY))
      .andExpect(status().isNotFound());
  }

  @Test
  void swaggerUi_opensOnThePublicGroup() throws Exception {
    mockMvc
      .perform(
        get("/v3/api-docs/swagger-config").header(KEY_HEADER, PUBLIC_KEY)
      )
      .andExpect(status().isOk())
      .andExpect(jsonPath("$['urls.primaryName']").value("Public API"))
      .andExpect(jsonPath("$.urls[0].url").value("/v3/api-docs/public"));
  }

  @Test
  void eachGroup_hasItsOwnApiKeyScheme() throws Exception {
    String publicSpec = spec("public");
    String adminSpec = spec("admin");

    assertThat(
      JsonPath.<Map<String, Object>>read(
        publicSpec,
        "$.components.securitySchemes"
      )
    )
      .containsOnlyKeys("publicApiKey");
    assertThat(
      JsonPath.<String>read(
        publicSpec,
        "$.components.securitySchemes.publicApiKey.name"
      )
    )
      .isEqualTo(KEY_HEADER);
    assertThat(
      JsonPath.<String>read(
        publicSpec,
        "$.components.securitySchemes.publicApiKey.in"
      )
    )
      .isEqualTo("header");
    assertThat(JsonPath.<List<?>>read(publicSpec, "$.security[*].publicApiKey"))
      .hasSize(1);
    assertThat(JsonPath.<String>read(publicSpec, "$.info.description"))
      .contains("`" + KEY_HEADER + "` header");
    assertThat(
      JsonPath.<Map<String, Object>>read(
        adminSpec,
        "$.components.securitySchemes"
      )
    )
      .containsOnlyKeys("adminApiKey");
    assertThat(JsonPath.<List<?>>read(adminSpec, "$.security[*].adminApiKey"))
      .hasSize(1);
  }

  @Test
  void everyOperation_documentsAuthFailureServerErrorAndAcceptLanguage()
    throws Exception {
    assertCommonParts(spec("public"), "401");
    assertCommonParts(spec("admin"), "403");
  }

  private static void assertCommonParts(String spec, String authFailure) {
    for (Map<String, Object> operation : operations(spec)) {
      String id = (String) operation.get("operationId");
      String json = JsonPath.parse(operation).jsonString();
      assertThat(JsonPath.<Map<String, Object>>read(json, "$.responses"))
        .as(id)
        .containsKeys(authFailure, "500");
      assertThat(
        JsonPath.<Map<String, Object>>read(json, "$.responses." + authFailure)
      )
        .as(id)
        .doesNotContainKeys("content", "headers");
      assertThat(JsonPath.<List<String>>read(json, "$.parameters[*].name"))
        .as(id)
        .contains("Accept-Language");
      assertThat(
        JsonPath.<Map<String, Object>>read(json, "$.responses.500.headers")
      )
        .as(id)
        .containsKeys("Content-Language", "Vary");
    }
  }

  /** springdoc builds one document per Accept-Language; none may pile up. */
  @Test
  void regeneratingForAnotherLocale_addsNothingTwice() throws Exception {
    for (String language : List.of("uk", "ja")) {
      String spec = mockMvc
        .perform(
          get("/v3/api-docs/public")
            .header(KEY_HEADER, PUBLIC_KEY)
            .header("Accept-Language", language)
        )
        .andExpect(status().isOk())
        .andReturn()
        .getResponse()
        .getContentAsString();

      assertThat(JsonPath.<List<?>>read(spec, "$.security")).hasSize(1);
      assertThat(
        JsonPath.<List<String>>read(
          spec,
          "$.paths['/api/v1/posts/{slug}'].get.parameters[*].name"
        )
      )
        .containsExactly("slug", "Accept-Language");
    }
  }

  @Test
  void operationIds_areExplicitAndUnique() throws Exception {
    List<String> ids = new ArrayList<>();
    ids.addAll(JsonPath.read(spec("public"), "$.paths.*.*.operationId"));
    ids.addAll(JsonPath.read(spec("admin"), "$.paths.*.*.operationId"));

    assertThat(ids).doesNotHaveDuplicates().noneMatch(id -> id.contains("_"));
    assertThat(ids).contains("listPosts", "getPost", "createPost", "deleteTag");
  }

  @Test
  void everyErrorCode_isDocumented() throws Exception {
    String publicSpec = spec("public");
    String adminSpec = spec("admin");
    List<String> exampleCodes = new ArrayList<>();
    exampleCodes.addAll(
      JsonPath.read(
        publicSpec,
        "$.paths.*.*.responses.*.content.*.examples.*.value.code"
      )
    );
    exampleCodes.addAll(
      JsonPath.read(
        adminSpec,
        "$.paths.*.*.responses.*.content.*.examples.*.value.code"
      )
    );
    String description = JsonPath.read(publicSpec, "$.info.description");
    List<String> codes = Arrays
      .stream(ErrorCode.values())
      .map(Enum::name)
      .toList();

    // The generic codes (any route) are documented once, in the description
    assertThat(exampleCodes)
      .containsAll(
        codes
          .stream()
          .filter(c -> !c.equals("UNSUPPORTED_REQUEST"))
          .filter(c -> !c.equals("REQUEST_REJECTED"))
          .toList()
      );
    assertThat(codes).allMatch(code -> description.contains("`" + code + "`"));
    assertThat(
      JsonPath.<List<String>>read(
        publicSpec,
        "$.components.schemas.ApiErrorResponse.properties.code.enum"
      )
    )
      .containsExactlyElementsOf(codes);
  }

  @Test
  void errorResponses_listTheirCodesWithExamples() throws Exception {
    String spec = spec("public");
    String notFound = "$.paths['/api/v1/posts/{slug}'].get.responses.404";

    assertThat(JsonPath.<String>read(spec, notFound + ".description"))
      .contains("`NOT_FOUND`", "`TRANSLATION_NOT_AVAILABLE`");
    assertThat(
      JsonPath.<String>read(
        spec,
        notFound +
        ".content['application/json'].examples.TRANSLATION_NOT_AVAILABLE.value.message"
      )
    )
      .isEqualTo("This post is not available in the requested language.");
    assertThat(
      JsonPath.<String>read(
        spec,
        notFound + ".content['application/json'].examples.NOT_FOUND.value.path"
      )
    )
      .isEqualTo("/api/v1/posts/{slug}");
  }

  @Test
  void responseFields_areRequiredUnlessNullable() throws Exception {
    String spec = spec("public");
    String detail = "$.components.schemas.PostDetailResponse";

    assertThat(JsonPath.<List<String>>read(spec, detail + ".required"))
      .contains(
        "slug",
        "title",
        "excerpt",
        "body",
        "metaTitle",
        "metaDescription",
        "availableLocales"
      );
    assertThat(JsonPath.<String>read(spec, detail + ".properties.title.type"))
      .isEqualTo("string");
    assertThat(
      JsonPath.<List<String>>read(
        spec,
        "$.components.schemas.TagResponse.properties.color.type"
      )
    )
      .containsExactly("string", "null");
    assertThat(
      JsonPath.<List<String>>read(
        spec,
        "$.components.schemas.ApiFieldError.properties.code.type"
      )
    )
      .containsExactly("string", "null");
  }

  @Test
  void serverError_isDocumentedPerOperation() throws Exception {
    String example =
      "$.paths['/api/v1/admin/tags/{id}'].delete.responses.500" +
      ".content['application/json'].examples.INTERNAL_ERROR.value";
    String spec = spec("admin");

    assertThat(JsonPath.<String>read(spec, example + ".path"))
      .isEqualTo("/api/v1/admin/tags/{id}");
    assertThat(JsonPath.<String>read(spec, example + ".message"))
      .isEqualTo("An unexpected error occurred.");
  }

  @Test
  void errorEnvelope_omitsNullsRatherThanSendingThem() throws Exception {
    String envelope = "$.components.schemas.ApiErrorResponse";
    String spec = spec("public");

    assertThat(JsonPath.<List<String>>read(spec, envelope + ".required"))
      .containsExactlyInAnyOrder("code", "message", "timestamp");
    assertThat(
      JsonPath.<String>read(spec, envelope + ".properties.traceId.type")
    )
      .isEqualTo("string");
  }

  @Test
  void adminSchemas_markDeletedAtAndPublishedAtNullable() throws Exception {
    String spec = spec("admin");

    assertThat(
      JsonPath.<List<String>>read(
        spec,
        "$.components.schemas.AdminPostResponse.properties.deletedAt.type"
      )
    )
      .containsExactly("string", "null");
    assertThat(
      JsonPath.<List<String>>read(
        spec,
        "$.components.schemas.AdminTranslationResponse.properties.publishedAt.type"
      )
    )
      .containsExactly("string", "null");
    assertThat(
      JsonPath.<List<String>>read(
        spec,
        "$.components.schemas.UpsertTranslationRequest.required"
      )
    )
      .containsExactlyInAnyOrder("title", "body");
  }

  @Test
  void bothLists_documentPageAndSize() throws Exception {
    assertThat(
      JsonPath.<List<String>>read(
        spec("public"),
        "$.paths['/api/v1/posts'].get.parameters[*].name"
      )
    )
      .containsExactlyInAnyOrder("page", "size", "Accept-Language");
    assertThat(
      JsonPath.<List<String>>read(
        spec("admin"),
        "$.paths['/api/v1/admin/posts'].get.parameters[*].name"
      )
    )
      .containsExactlyInAnyOrder("page", "size", "deleted", "Accept-Language");
  }

  @Test
  void createsAndDeletes_documentTheirRealStatuses() throws Exception {
    String spec = spec("admin");
    String posts = "$.paths['/api/v1/admin/posts']";

    assertThat(
      JsonPath.<Map<String, Object>>read(spec, posts + ".post.responses")
    )
      .containsKey("201")
      .doesNotContainKey("200");
    assertThat(
      JsonPath.<Map<String, Object>>read(
        spec,
        posts + ".post.responses.201.headers"
      )
    )
      .containsKey("Location");
    assertThat(
      JsonPath.<Map<String, Object>>read(
        spec,
        "$.paths['/api/v1/admin/tags/{id}'].delete.responses"
      )
    )
      .containsKey("204")
      .doesNotContainKey("200");
  }
}
