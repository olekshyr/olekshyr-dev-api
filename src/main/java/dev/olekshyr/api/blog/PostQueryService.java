package dev.olekshyr.api.blog;

import dev.olekshyr.api.shared.PageResponse;
import dev.olekshyr.api.shared.SupportedLocale;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Public reads: only posts visible in the requested locale. */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
class PostQueryService {

  private final PostTranslationRepository translations;

  PageResponse<PostSummaryResponse> listPublished(
    SupportedLocale locale,
    Pageable pageable
  ) {
    return PageResponse.of(
      translations.findVisibleIn(locale, pageable).map(PostSummaryResponse::of)
    );
  }

  PostDetailResponse getPublished(String slug, SupportedLocale locale) {
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
