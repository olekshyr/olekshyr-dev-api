package dev.olekshyr.api.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.util.Assert;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

  @Bean
  @Order(1)
  public SecurityFilterChain adminFilterChain(
    HttpSecurity http,
    @Value("${app.security.headerName}") String headerName,
    @Value("${app.security.adminApiKey}") String adminApiKey
  ) throws Exception {
    Assert.hasText(adminApiKey, "ADMIN_API_KEY must be set");
    http
      .securityMatcher("/api/v1/admin/**")
      .csrf(AbstractHttpConfigurer::disable)
      .authorizeHttpRequests(r -> r.anyRequest().authenticated())
      .httpBasic(AbstractHttpConfigurer::disable)
      .formLogin(AbstractHttpConfigurer::disable)
      .sessionManagement(s ->
        s.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
      )
      .addFilterBefore(
        new AdminKeyFilter(headerName, adminApiKey),
        UsernamePasswordAuthenticationFilter.class
      );
    return http.build();
  }

  @Bean
  @Order(2)
  public SecurityFilterChain publicFilterChain(
    HttpSecurity http,
    @Value("${app.security.headerName}") String headerName,
    @Value("${app.security.publicApiKey}") String publicApiKey
  ) throws Exception {
    Assert.hasText(publicApiKey, "PUBLIC_API_KEY must be set");
    http
      .securityMatcher("/**")
      .csrf(AbstractHttpConfigurer::disable)
      .authorizeHttpRequests(r -> r.anyRequest().authenticated())
      .httpBasic(AbstractHttpConfigurer::disable)
      .formLogin(AbstractHttpConfigurer::disable)
      .sessionManagement(s ->
        s.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
      )
      .addFilterBefore(
        new PublicKeyFilter(headerName, publicApiKey),
        UsernamePasswordAuthenticationFilter.class
      );
    return http.build();
  }
}
