package dev.olekshyr.api.security;

import jakarta.servlet.http.HttpServletResponse;

public class PublicKeyFilter extends ApiKeyFilter {

  public PublicKeyFilter(String headerName, String publicApiKey) {
    super(headerName, publicApiKey);
  }

  @Override
  protected int unauthorizedStatus() {
    return HttpServletResponse.SC_UNAUTHORIZED;
  }
}
