package dev.olekshyr.api.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

@Slf4j
public abstract class ApiKeyFilter extends OncePerRequestFilter {

  private final String headerName;
  private final byte[] expectedKeyBytes;

  protected ApiKeyFilter(String headerName, String expectedKey) {
    this.headerName = headerName;
    this.expectedKeyBytes = expectedKey.getBytes(StandardCharsets.UTF_8);
  }

  protected abstract int unauthorizedStatus();

  @Override
  protected void doFilterInternal(
    HttpServletRequest request,
    HttpServletResponse response,
    FilterChain filterChain
  ) throws ServletException, IOException {
    String header = request.getHeader(headerName);
    if (
      header != null &&
      MessageDigest.isEqual(
        header.getBytes(StandardCharsets.UTF_8),
        expectedKeyBytes
      )
    ) {
      SecurityContextHolder
        .getContext()
        .setAuthentication(
          new ApiKeyAuthentication(header, AuthorityUtils.NO_AUTHORITIES)
        );
      filterChain.doFilter(request, response);
    } else {
      log.error(
        "Invalid or missing API key for path: {}",
        request.getRequestURI()
      );
      SecurityContextHolder.clearContext();
      response.setStatus(unauthorizedStatus());
      response.setContentType(MediaType.APPLICATION_JSON_VALUE);
      PrintWriter writer = response.getWriter();
      writer.print("{\"error\":\"Invalid API Key\"}");
      writer.flush();
    }
  }
}
