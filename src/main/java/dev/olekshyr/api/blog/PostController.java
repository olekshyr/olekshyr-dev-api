package dev.olekshyr.api.blog;

import dev.olekshyr.api.shared.PageResponse;
import dev.olekshyr.api.shared.SupportedLocale;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Public read API; the locale comes from Accept-Language (D1). */
@RestController
@RequestMapping("/api/v1/posts")
@RequiredArgsConstructor
class PostController {

  private final PostQueryService posts;

  /**
   * Newest first. Spring resolves the Pageable (defaults and cap, D4); the spec
   * documents only page and size because any sort parameter is ignored.
   */
  @GetMapping
  @Parameter(
    in = ParameterIn.QUERY,
    name = "page",
    description = "Zero-based page index",
    schema = @Schema(type = "integer", defaultValue = "0", minimum = "0")
  )
  @Parameter(
    in = ParameterIn.QUERY,
    name = "size",
    description = "Page size; larger values are capped at 50",
    schema = @Schema(
      type = "integer",
      defaultValue = "10",
      minimum = "1",
      maximum = "50"
    )
  )
  PageResponse<PostSummaryResponse> list(
    @Parameter(hidden = true) Pageable pageable,
    Locale locale
  ) {
    return posts.listPublished(SupportedLocale.from(locale), pageable);
  }

  @GetMapping("/{slug}")
  PostDetailResponse get(@PathVariable String slug, Locale locale) {
    return posts.getPublished(slug, SupportedLocale.from(locale));
  }
}
