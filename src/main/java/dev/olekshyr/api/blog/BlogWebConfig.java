package dev.olekshyr.api.blog;

import org.springframework.context.annotation.Configuration;
import org.springframework.format.FormatterRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Query-parameter values of the public lists. A converter that throws makes
 * Spring report a type mismatch: 400 VALIDATION_FAILED naming the parameter.
 */
@Configuration(proxyBeanMethods = false)
class BlogWebConfig implements WebMvcConfigurer {

  @Override
  public void addFormatters(FormatterRegistry registry) {
    registry.addConverter(String.class, PostOrder.class, PostOrder::fromParam);
    registry.addConverter(
      String.class,
      ListInclude.class,
      ListInclude::fromParam
    );
  }
}
