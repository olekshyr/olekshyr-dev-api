package dev.olekshyr.api.blog;

import dev.olekshyr.api.shared.SupportedLocale;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Queries over visible posts whose shape depends on the request, so they're
 * built rather than declared. The post list and the tag counts share one
 * definition of "visible", so a tag's {@code postCount} always equals the
 * size of the list filtered by it (D29).
 */
interface VisiblePostQueries {
  /**
   * Ids of the posts visible in {@code locale} (any locale when null), in the
   * given order, optionally only those with the tag. A post sorts by its
   * earliest {@code publishedAt} among the counted translations: with a
   * locale that's the locale's own date, without one it's the same in every
   * locale, so pages line up across locales (D28).
   */
  Page<Long> findVisiblePostIds(
    @Nullable SupportedLocale locale,
    @Nullable String tag,
    PostOrder order,
    Pageable pageable
  );

  /** Tag id to the number of posts visible in {@code locale} (any when null). */
  Map<Long, Long> countVisiblePostsPerTag(@Nullable SupportedLocale locale);
}
