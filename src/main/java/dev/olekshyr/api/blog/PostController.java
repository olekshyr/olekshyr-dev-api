package dev.olekshyr.api.blog;

import dev.olekshyr.api.shared.ApiError;
import dev.olekshyr.api.shared.ErrorCode;
import dev.olekshyr.api.shared.PageParameters;
import dev.olekshyr.api.shared.PageResponse;
import dev.olekshyr.api.shared.SupportedLocale;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Public read API; the locale comes from Accept-Language (D1). */
@Tag(
  name = "Posts",
  description = "Published posts, in the locale `Accept-Language` picks."
)
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
  @Operation(
    operationId = "listPosts",
    summary = "List the posts visible in the locale",
    description = "Posts with a published translation in the resolved " +
    "locale, newest `publishedAt` first. Drafts and deleted posts never " +
    "appear."
  )
  @PageParameters
  PageResponse<PostSummaryResponse> list(
    @Parameter(hidden = true) Pageable pageable,
    Locale locale
  ) {
    return posts.listVisible(SupportedLocale.from(locale), pageable);
  }

  @GetMapping("/{slug}")
  @Operation(
    operationId = "getPost",
    summary = "Get a post by slug",
    description = "The post's translation in the resolved locale, if it's " +
    "published there. There's no fallback to another locale."
  )
  @ApiError(
    status = HttpStatus.NOT_FOUND,
    code = ErrorCode.NOT_FOUND,
    when = "No post with this slug is visible in any locale: an unknown " +
    "slug, a deleted post, or one with drafts only."
  )
  @ApiError(
    status = HttpStatus.NOT_FOUND,
    code = ErrorCode.TRANSLATION_NOT_AVAILABLE,
    when = "The post is visible in another locale, not this one. Show " +
    "\"not available in this language\"."
  )
  PostDetailResponse get(
    @Parameter(
      description = "Matched exactly: no case folding, and a malformed slug " +
      "is just not found.",
      example = "hello-world"
    ) @PathVariable String slug,
    Locale locale
  ) {
    return posts.getVisible(slug, SupportedLocale.from(locale));
  }
}
