package dev.olekshyr.api.shared;

import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.olekshyr.api.security.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@WebMvcTest(useDefaultFilters = false)
@Import(
  {
    SecurityConfig.class,
    LocaleConfig.class,
    GlobalExceptionHandler.class,
    LocaleConfigTest.TestController.class,
  }
)
@TestPropertySource(
  properties = {
    "app.security.headerName=X-API-Key",
    "app.security.publicApiKey=pub-key",
    "app.security.adminApiKey=admin-key",
  }
)
class LocaleConfigTest {

  @Autowired
  private MockMvc mockMvc;

  @Test
  void supportedLanguage_isResolved() throws Exception {
    mockMvc
      .perform(locale("uk"))
      .andExpect(content().string("uk"))
      .andExpect(header().string("Content-Language", "uk"));
  }

  @Test
  void regionalVariant_matchesOnLanguage() throws Exception {
    mockMvc
      .perform(locale("uk-UA"))
      .andExpect(content().string("uk"))
      .andExpect(header().string("Content-Language", "uk"));
  }

  @Test
  void firstSupportedLanguageByQuality_wins() throws Exception {
    mockMvc
      .perform(locale("fr, ja;q=0.8, uk;q=0.5"))
      .andExpect(content().string("ja"));
  }

  @Test
  void unsupportedLanguage_fallsBackToEnglish() throws Exception {
    mockMvc
      .perform(locale("fr"))
      .andExpect(content().string("en"))
      .andExpect(header().string("Content-Language", "en"));
  }

  @Test
  void missingHeader_fallsBackToEnglish() throws Exception {
    mockMvc
      .perform(get("/api/v1/test/locale").header("X-API-Key", "pub-key"))
      .andExpect(content().string("en"))
      .andExpect(header().string("Content-Language", "en"));
  }

  @Test
  void responseVariesByAcceptLanguage() throws Exception {
    mockMvc
      .perform(locale("ja"))
      .andExpect(header().stringValues("Vary", hasItem("Accept-Language")));
  }

  @Test
  void errorResponse_alsoCarriesLanguageHeaders() throws Exception {
    mockMvc
      .perform(
        get("/api/v1/does-not-exist")
          .header("X-API-Key", "pub-key")
          .header("Accept-Language", "ja")
      )
      .andExpect(status().isNotFound())
      .andExpect(header().string("Content-Language", "ja"))
      .andExpect(header().stringValues("Vary", hasItem("Accept-Language")));
  }

  @Test
  void pathOutsideApi_hasNoLanguageHeaders() throws Exception {
    mockMvc
      .perform(
        get("/test/outside")
          .header("X-API-Key", "pub-key")
          .header("Accept-Language", "ja")
      )
      .andExpect(header().doesNotExist("Content-Language"));
  }

  private static org.springframework.test.web.servlet.RequestBuilder locale(
    String acceptLanguage
  ) {
    return get("/api/v1/test/locale")
      .header("X-API-Key", "pub-key")
      .header("Accept-Language", acceptLanguage);
  }

  @RestController
  static class TestController {

    @GetMapping("/api/v1/test/locale")
    String locale() {
      return SupportedLocale.from(LocaleContextHolder.getLocale()).code();
    }

    @GetMapping("/test/outside")
    String outside() {
      return "ok";
    }
  }
}
