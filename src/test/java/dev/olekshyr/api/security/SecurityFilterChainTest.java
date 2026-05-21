package dev.olekshyr.api.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest
@Import(SecurityConfig.class)
@TestPropertySource(
  properties = {
    "app.security.headerName=X-API-Key",
    "app.security.publicApiKey=pub-key",
    "app.security.adminApiKey=admin-key",
  }
)
class SecurityFilterChainTest {

  @Autowired
  private MockMvc mockMvc;

  @Test
  void publicEndpoint_validPublicKey_returns200() throws Exception {
    mockMvc
      .perform(get("/greeting").header("X-API-Key", "pub-key"))
      .andExpect(status().isOk());
  }

  @Test
  void publicEndpoint_wrongKey_returns401() throws Exception {
    mockMvc
      .perform(get("/greeting").header("X-API-Key", "wrong"))
      .andExpect(status().isUnauthorized())
      .andExpect(content().string(""));
  }

  @Test
  void publicEndpoint_noHeader_returns401() throws Exception {
    mockMvc
      .perform(get("/greeting"))
      .andExpect(status().isUnauthorized())
      .andExpect(content().string(""));
  }

  @Test
  void publicEndpoint_adminKey_returns401() throws Exception {
    mockMvc
      .perform(get("/greeting").header("X-API-Key", "admin-key"))
      .andExpect(status().isUnauthorized())
      .andExpect(content().string(""));
  }

  @Test
  void adminEndpoint_validAdminKey_returns404() throws Exception {
    mockMvc
      .perform(get("/api/v1/admin/anything").header("X-API-Key", "admin-key"))
      .andExpect(status().isNotFound());
  }

  @Test
  void adminEndpoint_publicKey_returns403() throws Exception {
    mockMvc
      .perform(get("/api/v1/admin/anything").header("X-API-Key", "pub-key"))
      .andExpect(status().isForbidden())
      .andExpect(content().string(""));
  }

  @Test
  void adminEndpoint_wrongKey_returns403() throws Exception {
    mockMvc
      .perform(get("/api/v1/admin/anything").header("X-API-Key", "wrong"))
      .andExpect(status().isForbidden())
      .andExpect(content().string(""));
  }

  @Test
  void adminEndpoint_noHeader_returns403() throws Exception {
    mockMvc
      .perform(get("/api/v1/admin/anything"))
      .andExpect(status().isForbidden())
      .andExpect(content().string(""));
  }
}
