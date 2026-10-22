package dev.olekshyr.api.blog;

import dev.olekshyr.api.shared.FieldErrorCode;
import dev.olekshyr.api.shared.FieldViolation;
import dev.olekshyr.api.shared.PageResponse;
import dev.olekshyr.api.shared.SupportedLocale;
import java.time.Clock;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Admin reads and writes, by numeric id (D13). Writes flush before mapping the
 * response, so auditing has set every updatedAt it returns. A unique-constraint
 * race the pre-checks miss surfaces at flush as a 409 CONFLICT.
 */
@Service
@Transactional
@RequiredArgsConstructor
class PostAdminService {

  /** Fixed order (D16); any sort in the pageable is dropped (D4). */
  private static final Sort LIST_ORDER = Sort.by(
    Sort.Order.desc("updatedAt"),
    Sort.Order.desc("id")
  );

  private final PostRepository posts;
  private final TagRepository tags;
  private final BodySanitizer sanitizer;
  private final Clock clock;

  @Transactional(readOnly = true)
  PageResponse<AdminPostListItem> list(
    Pageable pageable,
    boolean includeDeleted
  ) {
    Pageable ordered = PageRequest.of(
      pageable.getPageNumber(),
      pageable.getPageSize(),
      LIST_ORDER
    );
    Page<Post> page = includeDeleted
      ? posts.findAll(ordered)
      : posts.findByDeletedAtIsNull(ordered);
    return PageResponse.of(page.map(AdminPostListItem::of));
  }

  /** Draft preview: every translation, any status, deleted posts too (D23). */
  @Transactional(readOnly = true)
  AdminPostResponse get(long id) {
    return AdminPostResponse.of(find(id));
  }

  AdminPostResponse create(String slug) {
    requireSlugAvailable(slug);
    Post post = posts.save(new Post(slug));
    return respond(post);
  }

  AdminPostResponse changeSlug(long id, String slug) {
    Post post = writable(id);
    post.changeSlug(slug, posts::existsBySlug);
    return respond(post);
  }

  /** Live at once if the translation is published (D11). */
  AdminPostResponse upsertTranslation(
    long id,
    SupportedLocale locale,
    UpsertTranslationRequest request
  ) {
    Post post = writable(id);
    String body = sanitizer.sanitize(request.body());
    if (body.isBlank()) {
      throw new BodyEmptyAfterSanitizingException();
    }
    post.putTranslation(locale, request.toContent(body), clock.instant());
    return respond(post);
  }

  AdminPostResponse publish(long id, SupportedLocale locale) {
    Post post = writable(id);
    post.publish(locale, clock.instant());
    return respond(post);
  }

  AdminPostResponse unpublish(long id, SupportedLocale locale) {
    Post post = writable(id);
    post.unpublish(locale, clock.instant());
    return respond(post);
  }

  void softDelete(long id) {
    find(id).softDelete(clock.instant());
  }

  /** Unknown slugs are reported by their index in the request (tagSlugs[i]). */
  AdminPostResponse setTags(long id, List<String> tagSlugs) {
    Post post = writable(id);
    Map<String, Tag> found = tags
      .findBySlugIn(new HashSet<>(tagSlugs))
      .stream()
      .collect(Collectors.toMap(Tag::getSlug, Function.identity()));
    List<FieldViolation> unknown = new ArrayList<>();
    for (int i = 0; i < tagSlugs.size(); i++) {
      if (!found.containsKey(tagSlugs.get(i))) {
        unknown.add(
          new FieldViolation("tagSlugs[" + i + "]", FieldErrorCode.UNKNOWN_TAG)
        );
      }
    }
    if (!unknown.isEmpty()) {
      throw new UnknownTagsException(unknown);
    }
    post.replaceTags(new HashSet<>(found.values()), clock.instant());
    return respond(post);
  }

  private Post find(long id) {
    return posts.findById(id).orElseThrow(() -> new PostNotFoundException(id));
  }

  /** Writes to a deleted post are 404s (D23), checked before anything else. */
  private Post writable(long id) {
    Post post = find(id);
    if (post.isDeleted()) {
      throw new PostDeletedException(post.getSlug());
    }
    return post;
  }

  /** Includes deleted posts: their slugs stay reserved (D10). */
  private void requireSlugAvailable(String slug) {
    if (posts.existsBySlug(slug)) {
      throw new DuplicateSlugException(slug);
    }
  }

  private AdminPostResponse respond(Post post) {
    posts.flush();
    return AdminPostResponse.of(post);
  }
}
