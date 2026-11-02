package dev.olekshyr.api.blog;

import dev.olekshyr.api.shared.ApiError;
import dev.olekshyr.api.shared.ErrorCode;
import dev.olekshyr.api.shared.PageParameters;
import dev.olekshyr.api.shared.PageResponse;
import dev.olekshyr.api.shared.SupportedLocale;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin post API (ADMIN_API_KEY). Accept-Language only picks the language of
 * error messages; content comes back in every locale (D15).
 */
@Tag(
  name = "Admin posts",
  description = "Posts by numeric id, with every translation in any status. " +
  "Each write returns the whole post, so every locale tab can refresh."
)
@RestController
@RequestMapping("/api/v1/admin/posts")
@RequiredArgsConstructor
class PostAdminController {

  private static final String BAD_ID = "`id` isn't a number.";
  private static final String BAD_ID_OR_LOCALE =
    "`id` isn't a number, or `locale` isn't `en`, `uk` or `ja`.";
  private static final String NO_POST = "No post with this id.";
  private static final String NOT_WRITABLE =
    "No post with this id, or it's deleted (a deleted post can't be written to).";
  private static final String BAD_SLUG =
    BAD_ID +
    " Or the body is malformed, or `slug` breaks a rule (`details`: " +
    "`NOT_BLANK`, `SIZE`, `PATTERN`).";
  private static final String SLUG_TAKEN =
    "Another post has this slug; deleted posts keep theirs.";

  private final PostAdminService posts;

  @GetMapping
  @Operation(
    operationId = "listAdminPosts",
    summary = "List posts",
    description = "Every post, most recently updated first, with each " +
    "locale's status. Deleted posts only with `deleted=true`."
  )
  @PageParameters
  @ApiError(
    status = HttpStatus.BAD_REQUEST,
    code = ErrorCode.VALIDATION_FAILED,
    when = "`deleted` isn't `true` or `false`."
  )
  PageResponse<AdminPostListItem> list(
    @Parameter(hidden = true) Pageable pageable,
    @Parameter(
      description = "`true` includes deleted posts alongside the rest."
    ) @RequestParam(defaultValue = "false") boolean deleted
  ) {
    return posts.list(pageable, deleted);
  }

  @GetMapping("/{id}")
  @Operation(
    operationId = "getAdminPost",
    summary = "Get a post",
    description = "Every translation in any status (the draft preview). A " +
    "deleted post is returned too, with `deletedAt` set."
  )
  @ApiError(
    status = HttpStatus.BAD_REQUEST,
    code = ErrorCode.VALIDATION_FAILED,
    when = BAD_ID
  )
  @ApiError(
    status = HttpStatus.NOT_FOUND,
    code = ErrorCode.NOT_FOUND,
    when = NO_POST
  )
  AdminPostResponse get(@PathVariable long id) {
    return posts.get(id);
  }

  @PostMapping
  @Operation(
    operationId = "createPost",
    summary = "Create a post",
    description = "A post with a slug and no translations yet."
  )
  @ApiResponse(
    responseCode = "201",
    description = "Created.",
    useReturnTypeSchema = true,
    headers = @Header(
      name = "Location",
      description = "URL of the new post.",
      schema = @Schema(type = "string", format = "uri")
    )
  )
  @ApiError(
    status = HttpStatus.BAD_REQUEST,
    code = ErrorCode.VALIDATION_FAILED,
    when = "The body is malformed, or `slug` breaks a rule (`details`: " +
    "`NOT_BLANK`, `SIZE`, `PATTERN`)."
  )
  @ApiError(
    status = HttpStatus.CONFLICT,
    code = ErrorCode.DUPLICATE_SLUG,
    when = SLUG_TAKEN
  )
  @ApiError(
    status = HttpStatus.CONFLICT,
    code = ErrorCode.CONFLICT,
    when = "A concurrent request took the slug first."
  )
  ResponseEntity<AdminPostResponse> create(
    @Valid @RequestBody CreatePostRequest request
  ) {
    AdminPostResponse created = posts.create(request.slug());
    return ResponseEntity
      .created(CreatedLocation.of(created.id()))
      .body(created);
  }

  @PutMapping("/{id}")
  @Operation(
    operationId = "changePostSlug",
    summary = "Change the slug",
    description = "Free until any translation is first published, frozen " +
    "after. Sending the current slug is a no-op, so the editor can resend " +
    "the whole form. There are no redirects from old slugs."
  )
  @ApiError(
    status = HttpStatus.BAD_REQUEST,
    code = ErrorCode.VALIDATION_FAILED,
    when = BAD_SLUG
  )
  @ApiError(
    status = HttpStatus.NOT_FOUND,
    code = ErrorCode.NOT_FOUND,
    when = NOT_WRITABLE
  )
  @ApiError(
    status = HttpStatus.CONFLICT,
    code = ErrorCode.SLUG_FROZEN,
    when = "The post was published in some locale, so its slug can't change."
  )
  @ApiError(
    status = HttpStatus.CONFLICT,
    code = ErrorCode.DUPLICATE_SLUG,
    when = SLUG_TAKEN
  )
  @ApiError(
    status = HttpStatus.CONFLICT,
    code = ErrorCode.CONFLICT,
    when = "A concurrent request took the slug first."
  )
  AdminPostResponse changeSlug(
    @PathVariable long id,
    @Valid @RequestBody UpdatePostRequest request
  ) {
    return posts.changeSlug(id, request.slug());
  }

  @DeleteMapping("/{id}")
  @Operation(
    operationId = "deletePost",
    summary = "Delete a post",
    description = "Soft delete: the post leaves the public API and the " +
    "default admin list, can't be written to or restored, and keeps its " +
    "slug reserved. Idempotent: deleting again keeps the first `deletedAt`."
  )
  @ApiResponse(responseCode = "204", description = "Deleted.")
  @ApiError(
    status = HttpStatus.BAD_REQUEST,
    code = ErrorCode.VALIDATION_FAILED,
    when = BAD_ID
  )
  @ApiError(
    status = HttpStatus.NOT_FOUND,
    code = ErrorCode.NOT_FOUND,
    when = NO_POST
  )
  ResponseEntity<Void> delete(@PathVariable long id) {
    posts.softDelete(id);
    return ResponseEntity.noContent().build();
  }

  @PutMapping("/{id}/tags")
  @Operation(
    operationId = "setPostTags",
    summary = "Replace the tags",
    description = "Replaces the post's whole tag set."
  )
  @ApiError(
    status = HttpStatus.BAD_REQUEST,
    code = ErrorCode.VALIDATION_FAILED,
    when = BAD_ID +
    " Or the body is malformed, or `tagSlugs` breaks a rule (`details`: " +
    "`NOT_NULL`, `SIZE` over 50 slugs, `NOT_BLANK` on an item), or names a " +
    "tag that doesn't exist (`UNKNOWN_TAG` on each such item, `tagSlugs[i]`)."
  )
  @ApiError(
    status = HttpStatus.NOT_FOUND,
    code = ErrorCode.NOT_FOUND,
    when = NOT_WRITABLE
  )
  AdminPostResponse setTags(
    @PathVariable long id,
    @Valid @RequestBody SetPostTagsRequest request
  ) {
    return posts.setTags(id, request.tagSlugs());
  }

  @PutMapping("/{id}/translations/{locale}")
  @Operation(
    operationId = "upsertPostTranslation",
    summary = "Save a translation",
    description = "Creates or replaces the translation in `locale`. A new " +
    "one starts as a draft; edits to a published one are live at once (to " +
    "stage a rewrite, unpublish first). Saving a draft needs only `title` " +
    "and `body`; publishing, and saving a published one, also need " +
    "`excerpt` and `metaDescription`."
  )
  @ApiError(
    status = HttpStatus.BAD_REQUEST,
    code = ErrorCode.VALIDATION_FAILED,
    when = BAD_ID_OR_LOCALE +
    " Or the body is malformed, or a field breaks a rule (`details`: " +
    "`NOT_BLANK` for `title` or `body`, also when the body is nothing but " +
    "markup the sanitizer removes, and for `excerpt` or `metaDescription` " +
    "when the translation is published; `SIZE` for any field)."
  )
  @ApiError(
    status = HttpStatus.NOT_FOUND,
    code = ErrorCode.NOT_FOUND,
    when = NOT_WRITABLE
  )
  AdminPostResponse upsertTranslation(
    @PathVariable long id,
    @Parameter(
      description = "Locale code; any case."
    ) @PathVariable SupportedLocale locale,
    @Valid @RequestBody UpsertTranslationRequest request
  ) {
    return posts.upsertTranslation(id, locale, request);
  }

  @PostMapping("/{id}/translations/{locale}/publish")
  @Operation(
    operationId = "publishPostTranslation",
    summary = "Publish a translation",
    description = "Makes the translation public. The first publish sets " +
    "`publishedAt` and freezes the slug; publishing again keeps the date."
  )
  @ApiError(
    status = HttpStatus.BAD_REQUEST,
    code = ErrorCode.VALIDATION_FAILED,
    when = BAD_ID_OR_LOCALE +
    " Or the translation lacks what publishing needs: a `NOT_BLANK` detail " +
    "for each of `excerpt` and `metaDescription` that's empty."
  )
  @ApiError(
    status = HttpStatus.NOT_FOUND,
    code = ErrorCode.NOT_FOUND,
    when = NOT_WRITABLE + " Or the post has no translation in `locale`."
  )
  AdminPostResponse publish(
    @PathVariable long id,
    @Parameter(
      description = "Locale code; any case."
    ) @PathVariable SupportedLocale locale
  ) {
    return posts.publish(id, locale);
  }

  @PostMapping("/{id}/translations/{locale}/unpublish")
  @Operation(
    operationId = "unpublishPostTranslation",
    summary = "Unpublish a translation",
    description = "Back to draft, gone from the public API. `publishedAt` " +
    "is kept, so publishing again restores the original date."
  )
  @ApiError(
    status = HttpStatus.BAD_REQUEST,
    code = ErrorCode.VALIDATION_FAILED,
    when = BAD_ID_OR_LOCALE
  )
  @ApiError(
    status = HttpStatus.NOT_FOUND,
    code = ErrorCode.NOT_FOUND,
    when = NOT_WRITABLE + " Or the post has no translation in `locale`."
  )
  AdminPostResponse unpublish(
    @PathVariable long id,
    @Parameter(
      description = "Locale code; any case."
    ) @PathVariable SupportedLocale locale
  ) {
    return posts.unpublish(id, locale);
  }
}
