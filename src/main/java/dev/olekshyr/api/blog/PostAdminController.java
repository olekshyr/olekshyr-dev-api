package dev.olekshyr.api.blog;

import dev.olekshyr.api.shared.PageResponse;
import dev.olekshyr.api.shared.SupportedLocale;
import io.swagger.v3.oas.annotations.Parameter;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
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
@RestController
@RequestMapping("/api/v1/admin/posts")
@RequiredArgsConstructor
class PostAdminController {

  private final PostAdminService posts;

  @GetMapping
  PageResponse<AdminPostListItem> list(
    @Parameter(hidden = true) Pageable pageable,
    @RequestParam(defaultValue = "false") boolean deleted
  ) {
    return posts.list(pageable, deleted);
  }

  @GetMapping("/{id}")
  AdminPostResponse get(@PathVariable long id) {
    return posts.get(id);
  }

  @PostMapping
  ResponseEntity<AdminPostResponse> create(
    @Valid @RequestBody CreatePostRequest request
  ) {
    AdminPostResponse created = posts.create(request.slug());
    return ResponseEntity
      .created(CreatedLocation.of(created.id()))
      .body(created);
  }

  @PutMapping("/{id}")
  AdminPostResponse changeSlug(
    @PathVariable long id,
    @Valid @RequestBody UpdatePostRequest request
  ) {
    return posts.changeSlug(id, request.slug());
  }

  @DeleteMapping("/{id}")
  ResponseEntity<Void> delete(@PathVariable long id) {
    posts.softDelete(id);
    return ResponseEntity.noContent().build();
  }

  @PutMapping("/{id}/tags")
  AdminPostResponse setTags(
    @PathVariable long id,
    @Valid @RequestBody SetPostTagsRequest request
  ) {
    return posts.setTags(id, request.tagSlugs());
  }

  @PutMapping("/{id}/translations/{locale}")
  AdminPostResponse upsertTranslation(
    @PathVariable long id,
    @PathVariable SupportedLocale locale,
    @Valid @RequestBody UpsertTranslationRequest request
  ) {
    return posts.upsertTranslation(id, locale, request);
  }

  @PostMapping("/{id}/translations/{locale}/publish")
  AdminPostResponse publish(
    @PathVariable long id,
    @PathVariable SupportedLocale locale
  ) {
    return posts.publish(id, locale);
  }

  @PostMapping("/{id}/translations/{locale}/unpublish")
  AdminPostResponse unpublish(
    @PathVariable long id,
    @PathVariable SupportedLocale locale
  ) {
    return posts.unpublish(id, locale);
  }
}
