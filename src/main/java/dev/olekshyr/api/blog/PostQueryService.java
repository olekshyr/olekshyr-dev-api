package dev.olekshyr.api.blog;

import dev.olekshyr.api.shared.PageResponse;
import dev.olekshyr.api.shared.SupportedLocale;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Limit;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Public reads: only visible posts. Both are cached by the resolved locale
 * (D3) and cleared on every admin write (D24); a not-found exception is never
 * cached.
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
class PostQueryService {

  private final PostTranslationRepository translations;

  /**
   * Keyed by every input (D29). An empty page isn't cached past the first,
   * nor for a tag at all, so requests for arbitrary page numbers or tag slugs
   * can't fill Redis with entries. Any sort in the pageable is dropped: the
   * order is {@code query.order()}.
   */
  @Cacheable(
    cacheNames = BlogCacheConfig.POST_LIST,
    key = "#query.cacheKey(#pageable)",
    unless = "#result.content().isEmpty() && " +
    "(#pageable.pageNumber > 0 || #query.tag() != null)"
  )
  PageResponse<PostSummaryResponse> listVisible(
    PostListQuery query,
    Pageable pageable
  ) {
    Page<Long> ids = translations.findVisiblePostIds(
      query.visibleIn(),
      query.tag(),
      query.order(),
      PageRequest.of(pageable.getPageNumber(), pageable.getPageSize())
    );
    Map<Long, List<PostTranslation>> published = ids.isEmpty()
      ? Map.of()
      : translations
        .findPublishedByPostIdIn(ids.getContent())
        .stream()
        .collect(Collectors.groupingBy(t -> t.getPost().getId()));
    return PageResponse.of(
      ids.map(id -> PostSummaryResponse.of(published.get(id), query.locale()))
    );
  }

  /**
   * The neighbours depend on other posts, but every admin write clears this
   * cache too (D24), so they can't go stale.
   */
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
    PostTranslation translation = translations
      .findVisibleBySlug(slug, locale)
      .orElseThrow(() -> new TranslationNotAvailableException(slug, locale));
    return PostDetailResponse.of(
      translation,
      availableLocales,
      first(
        translations.findVisibleBefore(
          locale,
          translation.getPublishedAt(),
          slug,
          Limit.of(1)
        )
      ),
      first(
        translations.findVisibleAfter(
          locale,
          translation.getPublishedAt(),
          slug,
          Limit.of(1)
        )
      )
    );
  }

  private static @Nullable PostLinkResponse first(
    List<PostTranslation> translations
  ) {
    return translations.isEmpty()
      ? null
      : PostLinkResponse.of(translations.getFirst());
  }
}
