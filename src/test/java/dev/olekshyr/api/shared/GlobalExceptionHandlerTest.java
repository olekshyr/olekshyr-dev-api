package dev.olekshyr.api.shared;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.olekshyr.api.security.SecurityConfig;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@WebMvcTest
@Import(
  {
    SecurityConfig.class,
    GlobalExceptionHandler.class,
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

  @Autowired
  private MockMvc mockMvc;

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
  }
}
