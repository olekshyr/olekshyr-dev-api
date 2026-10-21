package dev.olekshyr.api.shared;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.DispatcherServlet;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.i18n.AcceptHeaderLocaleResolver;

/** Locale comes from Accept-Language only (D1). */
@Configuration(proxyBeanMethods = false)
public class LocaleConfig {

  @Bean(name = DispatcherServlet.LOCALE_RESOLVER_BEAN_NAME)
  LocaleResolver localeResolver() {
    AcceptHeaderLocaleResolver resolver = new AcceptHeaderLocaleResolver();
    resolver.setSupportedLocales(SupportedLocale.javaLocales());
    resolver.setDefaultLocale(SupportedLocale.DEFAULT.toLocale());
    return resolver;
  }

  @Bean
  ContentLanguageFilter contentLanguageFilter(LocaleResolver localeResolver) {
    return new ContentLanguageFilter(localeResolver);
  }
}
