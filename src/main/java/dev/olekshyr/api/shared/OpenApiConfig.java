package dev.olekshyr.api.shared;

import io.swagger.v3.core.converter.ModelConverter;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.SpecVersion;
import io.swagger.v3.oas.models.headers.Header;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.parameters.HeaderParameter;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import java.lang.annotation.Annotation;
import java.util.Arrays;
import java.util.Set;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springdoc.core.models.GroupedOpenApi;
import org.springdoc.core.properties.SpringDocConfigProperties;
import org.springdoc.core.properties.SwaggerUiConfigProperties;
import org.springdoc.core.utils.SpringDocAnnotationsUtils;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import tools.jackson.databind.json.JsonMapper;

/**
 * The OpenAPI spec, the frontend's integration reference: one group per
 * API-key tier, each with its own security scheme. Error responses come from
 * {@link ApiError} on each endpoint, plus the 500 on every one.
 */
@Configuration
// Off with springdoc's own switch (as advised for prod): its beans are gone then
@ConditionalOnBooleanProperty(
  name = "springdoc.api-docs.enabled",
  matchIfMissing = true
)
class OpenApiConfig {

  private static final String PUBLIC_DISPLAY_NAME = "Public API";
  private static final String API_PATHS = "/api/v1/**";
  private static final String ADMIN_PATHS = "/api/v1/admin/**";
  private static final String CONTENT_LANGUAGE_REF =
    "#/components/headers/" + HttpHeaders.CONTENT_LANGUAGE;
  private static final String VARY_REF =
    "#/components/headers/" + HttpHeaders.VARY;

  private static final String DESCRIPTION =
    """
    Backend of olekshyr.dev's blog. Two documents, one per API key: \
    **public** (`/v3/api-docs/public`, the website's reads) and **admin** \
    (`/v3/api-docs/admin`, the post editor and tag management).

    ## Authentication
    Every request sends an API key in the `{headerName}` header. Public routes take \
    the public key and answer `401` when it's missing or wrong; admin routes \
    (`/api/v1/admin/**`) take the admin key and answer `403`. A key works only \
    on its own routes. Both rejections have an empty body.

    ## Locale
    `Accept-Language` picks the language. Supported: `en`, `uk`, `ja`, matched \
    by language (`uk-UA` → `uk`); a missing or unsupported header means `en`, \
    never an error. On public routes it picks the content and the error \
    messages; on admin routes only the error messages (content comes in every \
    locale). Every response except the empty `401`/`403` carries \
    `Content-Language` (the resolved locale) and `Vary: Accept-Language`. \
    Locales in JSON are these lowercase codes; lists of them are in \
    `en, uk, ja` order.

    ## Pagination
    List endpoints take `page` (zero-based) and `size` (default 10, at most \
    50). Out-of-range values are corrected, never rejected, a page past the \
    end returns empty `content`, and `sort` is ignored.

    ## Errors
    Every error except the empty `401`/`403` returns the `ApiErrorResponse` \
    envelope. `code` and `details[].code` are a stable contract: branch on \
    them, never on `message`, which is localized from `Accept-Language` and \
    meant to be shown as-is.

    | `code` | Status | When |
    |---|---|---|
    | `VALIDATION_FAILED` | 400 | Invalid input: malformed JSON, a path or query value of the wrong type, a body field breaking a rule (per-field `details`) |
    | `NOT_FOUND` | 404 | No such resource, or no route |
    | `TRANSLATION_NOT_AVAILABLE` | 404 | The post exists in another locale, not the requested one |
    | `UNSUPPORTED_REQUEST` | 405, 406, 415 | Wrong method, unacceptable `Accept`, or unsupported `Content-Type` |
    | `DUPLICATE_SLUG` | 409 | The slug is taken (deleted posts keep theirs) |
    | `SLUG_FROZEN` | 409 | The post's slug can't change once it was published |
    | `TAG_IN_USE` | 409 | The tag is still on a post |
    | `CONFLICT` | 409 | Another unique constraint, or a concurrent write took the value |
    | `REQUEST_REJECTED` | other 4xx | Rejected for another reason |
    | `INTERNAL_ERROR` | 500 | Server error; quote `traceId` when reporting it |

    Field-level codes in `details[].code`: `NOT_BLANK`, `NOT_NULL`, `SIZE` and \
    `PATTERN` (named after the broken rule) and `UNKNOWN_TAG`. \
    `details[].field` is the request field, with an index or key for an item \
    (`tagSlugs[1]`).
    """;

  @Bean
  OpenAPI openApi(@Value("${app.security.headerName}") String headerName) {
    return new OpenAPI()
      .info(
        new Info()
          .title("olekshyr.dev API")
          .version("v1")
          .description(DESCRIPTION.replace("{headerName}", headerName))
      );
  }

  @Bean
  GroupedOpenApi publicApi(
    @Value("${app.security.headerName}") String headerName,
    SpringDocConfigProperties springDoc
  ) {
    return GroupedOpenApi
      .builder()
      .group("public")
      .displayName(PUBLIC_DISPLAY_NAME)
      .pathsToMatch(API_PATHS)
      .pathsToExclude(ADMIN_PATHS)
      .addOpenApiCustomizer(
        tier(
          "publicApiKey",
          headerName,
          specVersion(springDoc),
          "The public key (`PUBLIC_API_KEY`).",
          HttpStatus.UNAUTHORIZED,
          "Picks the content language and the error-message language. " +
          "Supported: `en`, `uk`, `ja`, matched by language (`uk-UA` → `uk`); " +
          "missing or unsupported means `en`."
        )
      )
      .build();
  }

  @Bean
  GroupedOpenApi adminApi(
    @Value("${app.security.headerName}") String headerName,
    SpringDocConfigProperties springDoc
  ) {
    return GroupedOpenApi
      .builder()
      .group("admin")
      .displayName("Admin API")
      .pathsToMatch(ADMIN_PATHS)
      .addOpenApiCustomizer(
        tier(
          "adminApiKey",
          headerName,
          specVersion(springDoc),
          "The admin key (`ADMIN_API_KEY`).",
          HttpStatus.FORBIDDEN,
          "Picks the error-message language only: content always comes in " +
          "every locale. Supported: `en`, `uk`, `ja`; anything else means `en`."
        )
      )
      .build();
  }

  /**
   * Swagger UI opens on the public group. It matches groups by display name,
   * so this uses the same constant; set once the properties are bound.
   */
  @Bean
  SmartInitializingSingleton swaggerUiOpensOnPublicApi(
    ObjectProvider<SwaggerUiConfigProperties> swaggerUi
  ) {
    return () ->
      swaggerUi.ifAvailable(ui -> ui.setUrlsPrimaryName(PUBLIC_DISPLAY_NAME));
  }

  @Bean
  ApiErrorOperationCustomizer apiErrorOperationCustomizer(
    MessageSource messageSource
  ) {
    return new ApiErrorOperationCustomizer(messageSource);
  }

  /** Reads the API's own null handling, so the docs follow the wire format. */
  @Bean
  ModelConverter recordNullabilityConverter(JsonMapper jsonMapper) {
    return new RecordNullabilityConverter(
      jsonMapper
        .serializationConfig()
        .getDefaultPropertyInclusion()
        .getValueInclusion()
    );
  }

  /**
   * The parts that differ per tier: the key, the auth failure status and what
   * Accept-Language does. Also adds the locale headers to every response but
   * the auth failure, which the API-key filter sends before they're set.
   */
  private static OpenApiCustomizer tier(
    String scheme,
    String headerName,
    SpecVersion specVersion,
    String keyDescription,
    HttpStatus authFailure,
    String acceptLanguageDescription
  ) {
    return openApi -> {
      if (openApi.getComponents() == null) {
        openApi.components(new Components());
      }
      openApi
        .getComponents()
        .addSecuritySchemes(
          scheme,
          new SecurityScheme()
            .type(SecurityScheme.Type.APIKEY)
            .in(SecurityScheme.In.HEADER)
            .name(headerName)
            .description(keyDescription)
        )
        .addHeaders(
          HttpHeaders.CONTENT_LANGUAGE,
          new Header()
            .description("The resolved locale.")
            .schema(localeSchema())
        )
        .addHeaders(
          HttpHeaders.VARY,
          new Header()
            .description("Always includes `Accept-Language`.")
            .schema(stringSchema())
        );
      // The error responses point at the envelope, which no endpoint returns,
      // so register it through springdoc's own resolver
      SpringDocAnnotationsUtils.extractSchema(
        openApi.getComponents(),
        ApiErrorResponse.class,
        null,
        new Annotation[0],
        specVersion
      );
      openApi.addSecurityItem(new SecurityRequirement().addList(scheme));
      if (openApi.getPaths() == null) return;
      openApi
        .getPaths()
        .values()
        .stream()
        .flatMap(path -> path.readOperations().stream())
        .forEach(operation ->
          customize(operation, authFailure, acceptLanguageDescription)
        );
    };
  }

  private static void customize(
    Operation operation,
    HttpStatus authFailure,
    String acceptLanguageDescription
  ) {
    operation.addParametersItem(
      new HeaderParameter()
        .name(HttpHeaders.ACCEPT_LANGUAGE)
        .description(acceptLanguageDescription)
        .schema(stringSchema().example("uk"))
    );
    operation
      .getResponses()
      .values()
      .forEach(response ->
        response
          .addHeaderObject(
            HttpHeaders.CONTENT_LANGUAGE,
            new Header().$ref(CONTENT_LANGUAGE_REF)
          )
          .addHeaderObject(HttpHeaders.VARY, new Header().$ref(VARY_REF))
      );
    operation
      .getResponses()
      .addApiResponse(
        String.valueOf(authFailure.value()),
        new ApiResponse()
          .description(
            "The API key is missing or isn't this tier's key. Empty body."
          )
      );
  }

  private static SpecVersion specVersion(SpringDocConfigProperties springDoc) {
    return springDoc.isOpenapi31() ? SpecVersion.V31 : SpecVersion.V30;
  }

  private static Schema<String> stringSchema() {
    return new Schema<String>().types(Set.of("string"));
  }

  private static Schema<String> localeSchema() {
    Schema<String> schema = stringSchema();
    schema.setEnum(
      Arrays
        .stream(SupportedLocale.values())
        .map(SupportedLocale::code)
        .toList()
    );
    return schema;
  }
}
