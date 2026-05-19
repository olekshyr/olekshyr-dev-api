package dev.olekshyr.api.security;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class AuthenticationService {

  private final String authTokenHeaderName;
  private final String authToken;

  public AuthenticationService(
    @Value("${app.security.headerName}") String authTokenHeaderName,
    @Value("${app.security.apiKey}") String authToken
  ) {
    this.authTokenHeaderName = authTokenHeaderName;
    this.authToken = authToken;
  }

  public Authentication getAuthentication(HttpServletRequest request) {
    String apiKey = request.getHeader(authTokenHeaderName);
    if (apiKey == null || !apiKey.equals(authToken)) {
      log.error("Invalid API Key");
      throw new BadCredentialsException("Invalid API Key");
    }

    return new ApiKeyAuthentication(apiKey, AuthorityUtils.NO_AUTHORITIES);
  }
}
