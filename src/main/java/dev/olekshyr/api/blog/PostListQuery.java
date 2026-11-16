package dev.olekshyr.api.blog;

import dev.olekshyr.api.shared.SupportedLocale;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Pageable;

/**
 * What the public post list was asked for, besides the page.
 *
 * @param locale the resolved locale: the language of the content
 * @param tag a tag slug to filter by, or null for every post
 * @param order newest or oldest first
 * @param includeUntranslated also list posts visible only in other locales
 */
record PostListQuery(
  SupportedLocale locale,
  @Nullable String tag,
  PostOrder order,
  boolean includeUntranslated
) {
  /** The locale the posts must be visible in; null means any locale. */
  @Nullable
  SupportedLocale visibleIn() {
    return includeUntranslated ? null : locale;
  }

  /**
   * Every input of the response, tag last: it's the only free-form part, and
   * {@code *} can't be a slug (D10), so keys never collide. Public: the cache
   * key expression only finds public methods.
   */
  public String cacheKey(Pageable pageable) {
    return String.join(
      ":",
      locale.code(),
      String.valueOf(pageable.getPageNumber()),
      String.valueOf(pageable.getPageSize()),
      order.name().toLowerCase(),
      includeUntranslated ? "all" : "translated",
      tag == null ? "*" : tag
    );
  }
}
