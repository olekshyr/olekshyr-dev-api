package dev.olekshyr.api.blog;

import dev.olekshyr.api.shared.SupportedLocale;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** The public tag list: the filter chips of the post list (D29). */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
class TagQueryService {

  private final PostTranslationRepository translations;
  private final TagRepository tags;

  /**
   * Only tags with at least one post in the list, sorted by slug. Cached like
   * the post reads and cleared by the same writes (D24).
   */
  @Cacheable(
    cacheNames = BlogCacheConfig.TAG_LIST,
    key = "#locale.code() + ':' + (#includeUntranslated ? 'all' : 'translated')"
  )
  List<TagSummaryResponse> listInUse(
    SupportedLocale locale,
    boolean includeUntranslated
  ) {
    Map<Long, Long> counts = translations.countVisiblePostsPerTag(
      includeUntranslated ? null : locale
    );
    return tags
      .findAllById(counts.keySet())
      .stream()
      .sorted(Comparator.comparing(Tag::getSlug))
      .map(tag -> TagSummaryResponse.of(tag, locale, counts.get(tag.getId())))
      .toList();
  }
}
