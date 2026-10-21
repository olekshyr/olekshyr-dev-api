package dev.olekshyr.api.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/** Swagger UI and the OpenAPI spec skip the API key only when enabled (D22). */
class DocsFilterChainTest {

  @Nested
  @WebMvcTest(useDefaultFilters = false)
  @Import(SecurityConfig.class)
  @TestPropertySource(
    properties = {
      "app.security.headerName=X-API-Key",
      "app.security.publicApiKey=pub-key",
      "app.security.adminApiKey=admin-key",
      "app.docs.public=true",
    }
  )
  class WhenDocsArePublic {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void apiDocs_withoutKey_isNotBlocked() throws Exception {
      mockMvc.perform(get("/v3/api-docs")).andExpect(status().isNotFound());
    }

    @Test
    void swaggerUi_withoutKey_isNotBlocked() throws Exception {
      mockMvc
        .perform(get("/swagger-ui/index.html"))
        .andExpect(status().isNotFound());
    }

    @Test
    void yamlApiDocs_withoutKey_isNotBlocked() throws Exception {
      mockMvc
        .perform(get("/v3/api-docs.yaml"))
        .andExpect(status().isNotFound());
    }

    @Test
    void apiRoutes_stillNeedTheKey() throws Exception {
      mockMvc
        .perform(get("/api/v1/posts"))
        .andExpect(status().isUnauthorized());
    }
  }

  @Nested
  @WebMvcTest(useDefaultFilters = false)
  @Import(SecurityConfig.class)
  @TestPropertySource(
    properties = {
      "app.security.headerName=X-API-Key",
      "app.security.publicApiKey=pub-key",
      "app.security.adminApiKey=admin-key",
    }
  )
  class ByDefault {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void apiDocs_withoutKey_returns401() throws Exception {
      mockMvc.perform(get("/v3/api-docs")).andExpect(status().isUnauthorized());
    }
  }
}
