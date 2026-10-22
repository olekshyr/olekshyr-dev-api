package dev.olekshyr.api.blog;

import dev.olekshyr.api.shared.PageResponse;
import dev.olekshyr.api.shared.SupportedLocale;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Public reads: only posts visible in the requested locale. Both are cached by
 * the resolved locale (D3) and cleared on every admin write (D24); a not-found
 * exception is never cached.
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
class PostQueryService {

  private final PostTranslationRepository translations;

  /**
   * Keyed by page and size only: the sort is ignored (D4). An empty page past
   * the first isn't cached, so requests for arbitrary page numbers can't fill
   * Redis with entries.
   */
  @Cacheable(
    cacheNames = BlogCacheConfig.POST_LIST,
    key = "#locale.code() + ':' + #pageable.pageNumber + ':' + #pageable.pageSize",
    unless = "#result.content().isEmpty() && #pageable.pageNumber > 0"
  )
  PageResponse<PostSummaryResponse> listVisible(
    SupportedLocale locale,
    Pageable pageable
  ) {
    return PageResponse.of(
      translations.findVisibleIn(locale, pageable).map(PostSummaryResponse::of)
    );
  }

  @Cacheable(
    cacheNames = BlogCacheConfig.POST_DETAIL,
    key = "#slug + ':' + #locale.code()"
  )
  PostDetailResponse getVisible(String slug, SupportedLocale locale) {
    List<SupportedLocale> availableLocales = translations
      .findVisibleLocalesBySlug(slug)
      .stream()
      .sorted()
      .toList();
    if (availableLocales.isEmpty()) {
      throw new PostNotFoundException(slug);
    }
    return translations
      .findVisibleBySlug(slug, locale)
      .map(translation -> PostDetailResponse.of(translation, availableLocales))
      .orElseThrow(() -> new TranslationNotAvailableException(slug, locale));
  }
}
