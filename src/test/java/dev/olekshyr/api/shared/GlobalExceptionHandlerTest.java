package dev.olekshyr.api.shared;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.olekshyr.api.security.SecurityConfig;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@WebMvcTest(useDefaultFilters = false)
@Import(
  {
    SecurityConfig.class,
    GlobalExceptionHandler.class,
    GlobalExceptionHandlerTest.FixedClock.class,
    GlobalExceptionHandlerTest.TestController.class,
  }
)
@TestPropertySource(
  properties = {
    "app.security.headerName=X-API-Key",
    "app.security.publicApiKey=pub-key",
    "app.security.adminApiKey=admin-key",
  }
)
class GlobalExceptionHandlerTest {

  private static final Instant NOW = Instant.parse("2026-03-01T12:00:00Z");

  @Autowired
  private MockMvc mockMvc;

  @TestConfiguration(proxyBeanMethods = false)
  static class FixedClock {

    @Bean
    Clock clock() {
      return Clock.fixed(NOW, ZoneOffset.UTC);
    }
  }

  @Test
  void timestampComesFromTheApplicationClock() throws Exception {
    mockMvc
      .perform(get("/does/not/exist").header("X-API-Key", "pub-key"))
      .andExpect(jsonPath("$.timestamp").value("2026-03-01T12:00:00Z"));
  }

  @Test
  void validationError_returns400WithDetails() throws Exception {
    mockMvc
      .perform(
        post("/test/validate")
          .header("X-API-Key", "pub-key")
          .contentType(MediaType.APPLICATION_JSON)
          .content("{\"email\":\"not-an-email\",\"name\":\"\"}")
      )
      .andExpect(status().isBadRequest())
      .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
      .andExpect(jsonPath("$.message").value("Request validation failed."))
      .andExpect(jsonPath("$.timestamp").exists())
      .andExpect(jsonPath("$.path").value("/test/validate"))
      .andExpect(jsonPath("$.details").isArray())
      .andExpect(jsonPath("$.details.length()").value(2));
  }

  @Test
  void validationError_withUkrainianLocale_returnsTranslatedMessage()
    throws Exception {
    mockMvc
      .perform(
        post("/test/validate")
          .header("X-API-Key", "pub-key")
          .header("Accept-Language", "uk")
          .contentType(MediaType.APPLICATION_JSON)
          .content("{\"email\":\"not-an-email\",\"name\":\"\"}")
      )
      .andExpect(status().isBadRequest())
      .andExpect(jsonPath("$.message").value("Помилка валідації запиту."));
  }

  @Test
  void unmappedPath_returns404WithEnvelope() throws Exception {
    mockMvc
      .perform(get("/does/not/exist").header("X-API-Key", "pub-key"))
      .andExpect(status().isNotFound())
      .andExpect(jsonPath("$.code").value("NOT_FOUND"))
      .andExpect(
        jsonPath("$.message").value("The requested resource was not found.")
      )
      .andExpect(jsonPath("$.timestamp").exists())
      .andExpect(jsonPath("$.path").value("/does/not/exist"))
      .andExpect(jsonPath("$.details").doesNotExist());
  }

  @Test
  void runtimeException_returns500WithEnvelope() throws Exception {
    mockMvc
      .perform(get("/test/error").header("X-API-Key", "pub-key"))
      .andExpect(status().isInternalServerError())
      .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
      .andExpect(jsonPath("$.message").value("An unexpected error occurred."))
      .andExpect(jsonPath("$.timestamp").exists())
      .andExpect(jsonPath("$.path").value("/test/error"))
      .andExpect(jsonPath("$.details").doesNotExist());
  }

  @Test
  void apiException_usesItsStatusAndCode() throws Exception {
    mockMvc
      .perform(get("/test/api-exception").header("X-API-Key", "pub-key"))
      .andExpect(status().isNotFound())
      .andExpect(jsonPath("$.code").value("TRANSLATION_NOT_AVAILABLE"))
      .andExpect(
        jsonPath("$.message")
          .value("This post is not available in the requested language.")
      )
      .andExpect(jsonPath("$.details").doesNotExist());
  }

  @Test
  void apiException_withJapaneseLocale_returnsTranslatedMessage()
    throws Exception {
    mockMvc
      .perform(
        get("/test/api-exception")
          .header("X-API-Key", "pub-key")
          .header("Accept-Language", "ja")
      )
      .andExpect(
        jsonPath("$.message")
          .value("この記事はリクエストされた言語では公開されていません。")
      );
  }

  @Test
  void apiExceptionWithViolations_returnsLocalizedDetails() throws Exception {
    mockMvc
      .perform(
        get("/test/api-exception-details")
          .header("X-API-Key", "pub-key")
          .header("Accept-Language", "uk")
      )
      .andExpect(status().isBadRequest())
      .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
      .andExpect(jsonPath("$.details.length()").value(1))
      .andExpect(jsonPath("$.details[0].field").value("excerpt"))
      .andExpect(jsonPath("$.details[0].code").value("NOT_BLANK"))
      .andExpect(
        jsonPath("$.details[0].message").value("Поле не може бути порожнім.")
      );
  }

  @Test
  void dataIntegrityViolation_returns409Conflict() throws Exception {
    mockMvc
      .perform(get("/test/integrity").header("X-API-Key", "pub-key"))
      .andExpect(status().isConflict())
      .andExpect(jsonPath("$.code").value("CONFLICT"))
      .andExpect(jsonPath("$.details").doesNotExist());
  }

  @Test
  void wrongMethod_returns405UnsupportedRequest() throws Exception {
    mockMvc
      .perform(post("/test/error").header("X-API-Key", "pub-key"))
      .andExpect(status().isMethodNotAllowed())
      .andExpect(jsonPath("$.code").value("UNSUPPORTED_REQUEST"))
      .andExpect(jsonPath("$.path").value("/test/error"));
  }

  @Test
  void unsupportedMediaType_returns415UnsupportedRequest() throws Exception {
    mockMvc
      .perform(
        post("/test/validate")
          .header("X-API-Key", "pub-key")
          .contentType(MediaType.TEXT_PLAIN)
          .content("hello")
      )
      .andExpect(status().isUnsupportedMediaType())
      .andExpect(jsonPath("$.code").value("UNSUPPORTED_REQUEST"));
  }

  @Test
  void malformedJson_returns400ValidationFailed() throws Exception {
    mockMvc
      .perform(
        post("/test/validate")
          .header("X-API-Key", "pub-key")
          .contentType(MediaType.APPLICATION_JSON)
          .content("{not json")
      )
      .andExpect(status().isBadRequest())
      .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
  }

  @Test
  void typeMismatch_returns400ValidationFailed() throws Exception {
    mockMvc
      .perform(
        get("/test/number").param("n", "abc").header("X-API-Key", "pub-key")
      )
      .andExpect(status().isBadRequest())
      .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
  }

  @Test
  void conflictStatus_returnsConflictCode() throws Exception {
    mockMvc
      .perform(get("/test/status/409").header("X-API-Key", "pub-key"))
      .andExpect(status().isConflict())
      .andExpect(jsonPath("$.code").value("CONFLICT"));
  }

  @Test
  void otherClientError_returnsRequestRejected() throws Exception {
    mockMvc
      .perform(get("/test/status/413").header("X-API-Key", "pub-key"))
      .andExpect(status().isPayloadTooLarge())
      .andExpect(jsonPath("$.code").value("REQUEST_REJECTED"));
  }

  @Test
  void nonStandardClientStatus_keepsStatusWithEnvelope() throws Exception {
    mockMvc
      .perform(get("/test/status/499").header("X-API-Key", "pub-key"))
      .andExpect(status().is(499))
      .andExpect(jsonPath("$.code").value("REQUEST_REJECTED"));
  }

  @Test
  void nonStandardServerStatus_returnsInternalError() throws Exception {
    mockMvc
      .perform(get("/test/status/599").header("X-API-Key", "pub-key"))
      .andExpect(status().is(599))
      .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"));
  }

  static class NotAvailableException extends ApiException {

    NotAvailableException() {
      super(
        ErrorCode.TRANSLATION_NOT_AVAILABLE,
        HttpStatus.NOT_FOUND,
        "not available"
      );
    }
  }

  static class MissingExcerptException extends ApiException {

    MissingExcerptException() {
      super(
        ErrorCode.VALIDATION_FAILED,
        HttpStatus.BAD_REQUEST,
        "missing excerpt",
        List.of(new FieldViolation("excerpt", FieldErrorCode.NOT_BLANK))
      );
    }
  }

  @RestController
  static class TestController {

    record CreateRequest(@NotBlank String name, @Email String email) {}

    @PostMapping("/test/validate")
    ResponseEntity<Void> validate(@Valid @RequestBody CreateRequest body) {
      return ResponseEntity.ok().build();
    }

    @GetMapping("/test/error")
    ResponseEntity<Void> error() {
      throw new RuntimeException("boom");
    }

    @GetMapping("/test/api-exception")
    ResponseEntity<Void> apiException() {
      throw new NotAvailableException();
    }

    @GetMapping("/test/api-exception-details")
    ResponseEntity<Void> apiExceptionDetails() {
      throw new MissingExcerptException();
    }

    @GetMapping("/test/integrity")
    ResponseEntity<Void> integrity() {
      throw new DataIntegrityViolationException("duplicate key");
    }

    @GetMapping("/test/status/{code}")
    ResponseEntity<Void> status(@PathVariable int code) {
      throw new ResponseStatusException(HttpStatusCode.valueOf(code));
    }

    @GetMapping("/test/number")
    ResponseEntity<Void> number(@RequestParam int n) {
      return ResponseEntity.ok().build();
    }
  }
}
