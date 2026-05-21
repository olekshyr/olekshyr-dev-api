package dev.olekshyr.api.security;

import jakarta.servlet.http.HttpServletResponse;

public class AdminKeyFilter extends ApiKeyFilter {

  public AdminKeyFilter(String headerName, String adminApiKey) {
    super(headerName, adminApiKey);
  }

  @Override
  protected int unauthorizedStatus() {
    return HttpServletResponse.SC_FORBIDDEN;
  }
}
