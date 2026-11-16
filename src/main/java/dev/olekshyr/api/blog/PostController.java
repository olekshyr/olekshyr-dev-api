package dev.olekshyr.api.blog;

import dev.olekshyr.api.shared.ApiError;
import dev.olekshyr.api.shared.ErrorCode;
import dev.olekshyr.api.shared.PageParameters;
import dev.olekshyr.api.shared.PageResponse;
import dev.olekshyr.api.shared.SupportedLocale;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
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
   * Spring resolves the Pageable (defaults and cap, D4); its own reading of
   * {@code sort} is unused, the {@code sort} parameter below decides (D27).
   */
  @GetMapping
  @Operation(
    operationId = "listPosts",
    summary = "List the posts visible in the locale",
    description = "Posts with a published translation in the resolved " +
    "locale, newest `publishedAt` first; with `include=untranslated`, every " +
    "post visible in any locale. Drafts and deleted posts never appear."
  )
  @PageParameters
  @ApiError(
    status = HttpStatus.BAD_REQUEST,
    code = ErrorCode.VALIDATION_FAILED,
    when = "`sort` or `include` has an unsupported value; `details` names " +
    "the parameter (`INVALID_VALUE`)."
  )
  PageResponse<PostSummaryResponse> list(
    @Parameter(hidden = true) Pageable pageable,
    @Parameter(
      description = "Only posts with this tag slug (exact match). An " +
      "unknown slug gives an empty page, not an error.",
      example = "java"
    ) @RequestParam(required = false) @Nullable String tag,
    @Parameter(
      description = "By `publishedAt`, ties by `slug` in the same " +
      "direction. With `include=untranslated` a post sorts by its earliest " +
      "`publishedAt` in any locale, so every locale has the same order and " +
      "the same pages.",
      schema = @Schema(
        type = "string",
        allowableValues = { "publishedAt,desc", "publishedAt,asc" },
        defaultValue = PostOrder.DEFAULT_PARAM
      )
    ) @RequestParam(defaultValue = PostOrder.DEFAULT_PARAM) PostOrder sort,
    @Parameter(
      description = "`untranslated` also lists posts not published in the " +
      "locale (`translated: false`, content in the item's `locale`).",
      schema = @Schema(type = "string", allowableValues = { "untranslated" })
    ) @RequestParam(required = false) @Nullable ListInclude include,
    Locale locale
  ) {
    PostListQuery query = new PostListQuery(
      SupportedLocale.from(locale),
      tag == null || tag.isBlank() ? null : tag,
      sort,
      include == ListInclude.UNTRANSLATED
    );
    return posts.listVisible(query, pageable);
  }

  @GetMapping("/{slug}")
  @Operation(
    operationId = "getPost",
    summary = "Get a post by slug",
    description = "The post's translation in the resolved locale, if it's " +
    "published there. There's no fallback to another locale. `previous` " +
    "and `next` link the neighbouring posts published in the locale."
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
