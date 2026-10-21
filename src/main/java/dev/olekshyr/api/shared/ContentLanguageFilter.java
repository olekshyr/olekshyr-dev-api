package dev.olekshyr.api.shared;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpHeaders;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.LocaleResolver;

/**
 * Labels every /api/v1 response with its resolved language so HTTP caches keep
 * languages apart (D3). Runs after the security filters, so the empty-body
 * 401/403 responses from the API-key filters are not labeled.
 */
class ContentLanguageFilter extends OncePerRequestFilter {

  private static final String API_PREFIX = "/api/v1/";

  private final LocaleResolver localeResolver;

  ContentLanguageFilter(LocaleResolver localeResolver) {
    this.localeResolver = localeResolver;
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    return !request.getRequestURI().startsWith(API_PREFIX);
  }

  @Override
  protected void doFilterInternal(
    HttpServletRequest request,
    HttpServletResponse response,
    FilterChain filterChain
  ) throws ServletException, IOException {
    SupportedLocale locale = SupportedLocale.from(
      localeResolver.resolveLocale(request)
    );
    response.setHeader(HttpHeaders.CONTENT_LANGUAGE, locale.code());
    response.addHeader(HttpHeaders.VARY, HttpHeaders.ACCEPT_LANGUAGE);
    filterChain.doFilter(request, response);
  }
}
