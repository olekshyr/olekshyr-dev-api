package dev.olekshyr.api.blog;

import java.time.Clock;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Admin tag management. A duplicate name in a locale (uq_tag_name_locale) has
 * no dedicated code and surfaces at flush as a 409 CONFLICT.
 */
@Service
@Transactional
@RequiredArgsConstructor
class TagAdminService {

  private final TagRepository tags;
  private final Clock clock;

  /** A small fixed vocabulary, so not paginated. */
  @Transactional(readOnly = true)
  List<AdminTagResponse> list() {
    return tags
      .findAll(Sort.by("slug"))
      .stream()
      .map(AdminTagResponse::of)
      .toList();
  }

  @Transactional(readOnly = true)
  AdminTagResponse get(long id) {
    return AdminTagResponse.of(find(id));
  }

  @EvictsPublicPosts
  AdminTagResponse create(UpsertTagRequest request) {
    requireSlugAvailable(request.slug());
    Tag tag = new Tag(request.slug(), request.color());
    tag.replaceNames(request.names(), clock.instant());
    return respond(tags.save(tag));
  }

  /** PUT replaces slug, color and names (D14). */
  @EvictsPublicPosts
  AdminTagResponse update(long id, UpsertTagRequest request) {
    Tag tag = find(id);
    tag.changeSlug(request.slug(), tags::existsBySlug);
    tag.changeColor(request.color());
    tag.replaceNames(request.names(), clock.instant());
    return respond(tag);
  }

  /**
   * Checked here rather than by mapping the FK violation, which looks like any
   * other constraint violation. The FK stays as the backstop for races.
   */
  @EvictsPublicPosts
  void delete(long id) {
    Tag tag = find(id);
    long postCount = tags.countPostsUsing(id);
    if (postCount > 0) {
      throw new TagInUseException(tag.getSlug(), postCount);
    }
    tags.delete(tag);
    tags.flush();
  }

  private Tag find(long id) {
    return tags.findById(id).orElseThrow(() -> new TagNotFoundException(id));
  }

  private void requireSlugAvailable(String slug) {
    if (tags.existsBySlug(slug)) {
      throw new DuplicateSlugException(slug);
    }
  }

  private AdminTagResponse respond(Tag tag) {
    tags.flush();
    return AdminTagResponse.of(tag);
  }
}
