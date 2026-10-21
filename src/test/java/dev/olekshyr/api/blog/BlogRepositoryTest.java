package dev.olekshyr.api.blog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.olekshyr.api.PostgresContainerConfiguration;
import dev.olekshyr.api.shared.JpaAuditingConfig;
import dev.olekshyr.api.shared.SupportedLocale;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.JdbcTemplate;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({ PostgresContainerConfiguration.class, JpaAuditingConfig.class })
class BlogRepositoryTest {

  private static final Instant BASE = Instant.parse("2026-01-01T00:00:00Z");

  @Autowired
  PostRepository posts;

  @Autowired
  PostTranslationRepository translations;

  @Autowired
  TagRepository tags;

  @Autowired
  EntityManager em;

  @Autowired
  JdbcTemplate jdbc;

  private static PostContent content(String title) {
    return new PostContent(title, "Excerpt", "Body", null, "Meta description");
  }

  private Post savePost(String slug, SupportedLocale locale, int daysOffset) {
    Post post = new Post(slug);
    post.putTranslation(locale, content(slug));
    post.publish(locale, BASE.plusSeconds(daysOffset * 86400L));
    return posts.saveAndFlush(post);
  }

  private void clear() {
    em.flush();
    em.clear();
  }

  @Test
  void publicListReturnsOnlyPublishedInLocaleNewestFirst() {
    savePost("old", SupportedLocale.EN, 1);
    savePost("new", SupportedLocale.EN, 3);
    savePost("middle", SupportedLocale.EN, 2);
    savePost("ukrainian", SupportedLocale.UK, 4);
    Post draft = new Post("draft");
    draft.putTranslation(SupportedLocale.EN, content("draft"));
    posts.save(draft);
    Post unpublished = savePost("unpublished", SupportedLocale.EN, 5);
    unpublished.unpublish(SupportedLocale.EN);
    clear();

    Page<PostTranslation> page = translations.findVisibleIn(
      SupportedLocale.EN,
      PageRequest.of(0, 10)
    );

    assertThat(page.getContent())
      .extracting(t -> t.getPost().getSlug())
      .containsExactly("new", "middle", "old");
    assertThat(page.getTotalElements()).isEqualTo(3);
  }

  @Test
  void publicListExcludesSoftDeletedPosts() {
    savePost("kept", SupportedLocale.EN, 1);
    Post deleted = savePost("deleted", SupportedLocale.EN, 2);
    deleted.softDelete(BASE);
    clear();

    Page<PostTranslation> page = translations.findVisibleIn(
      SupportedLocale.EN,
      PageRequest.of(0, 10)
    );

    assertThat(page.getContent())
      .extracting(t -> t.getPost().getSlug())
      .containsExactly("kept");
    assertThat(page.getTotalElements()).isEqualTo(1);
  }

  @Test
  void publicListPaginatesWithCorrectCounts() {
    for (int i = 1; i <= 5; i++) {
      savePost("post-" + i, SupportedLocale.EN, i);
    }
    clear();

    Page<PostTranslation> second = translations.findVisibleIn(
      SupportedLocale.EN,
      PageRequest.of(1, 2)
    );

    assertThat(second.getTotalElements()).isEqualTo(5);
    assertThat(second.getTotalPages()).isEqualTo(3);
    assertThat(second.getContent())
      .extracting(t -> t.getPost().getSlug())
      .containsExactly("post-3", "post-2");
  }

  @Test
  void visibleBySlugRespectsLocaleStatusAndDeletion() {
    savePost("hello", SupportedLocale.EN, 1);
    Post gone = savePost("gone", SupportedLocale.EN, 1);
    gone.softDelete(BASE);
    clear();

    assertThat(translations.findVisibleBySlug("hello", SupportedLocale.EN))
      .isPresent();
    assertThat(translations.findVisibleBySlug("hello", SupportedLocale.UK))
      .isEmpty();
    assertThat(translations.findVisibleBySlug("gone", SupportedLocale.EN))
      .isEmpty();
    assertThat(translations.findVisibleBySlug("missing", SupportedLocale.EN))
      .isEmpty();
  }

  @Test
  void availableLocalesListOnlyPublishedTranslations() {
    Post post = new Post("multi");
    post.putTranslation(SupportedLocale.EN, content("en"));
    post.putTranslation(SupportedLocale.UK, content("uk"));
    post.putTranslation(SupportedLocale.JA, content("ja"));
    post.publish(SupportedLocale.EN, BASE);
    post.publish(SupportedLocale.JA, BASE);
    posts.saveAndFlush(post);
    clear();

    assertThat(translations.findVisibleLocalesBySlug("multi"))
      .containsExactly(SupportedLocale.EN, SupportedLocale.JA);
  }

  @Test
  void slugOfDeletedPostStaysReserved() {
    Post post = savePost("reserved", SupportedLocale.EN, 1);
    post.softDelete(BASE);
    clear();

    assertThat(posts.existsBySlug("reserved")).isTrue();
    assertThat(posts.findBySlugAndDeletedAtIsNull("reserved")).isEmpty();
    assertThat(posts.existsBySlug("other")).isFalse();
  }

  @Test
  void adminListCanExcludeDeleted() {
    savePost("a", SupportedLocale.EN, 1);
    savePost("b", SupportedLocale.EN, 1).softDelete(BASE);
    clear();

    assertThat(posts.findByDeletedAtIsNull(PageRequest.of(0, 10)))
      .extracting(Post::getSlug)
      .containsExactly("a");
    assertThat(posts.findAll(PageRequest.of(0, 10)).getTotalElements())
      .isEqualTo(2);
  }

  @Test
  void postPersistsTagsTranslationsAndAuditTimestamps() {
    Tag tag = new Tag("java", "#ff0000");
    tag.putName(SupportedLocale.EN, "Java");
    tags.saveAndFlush(tag);
    Post post = new Post("with-tags");
    post.putTranslation(SupportedLocale.EN, content("t"));
    post.replaceTags(Set.of(tag));
    posts.saveAndFlush(post);
    clear();

    Post loaded = posts.findBySlugAndDeletedAtIsNull("with-tags").orElseThrow();

    assertThat(loaded.getCreatedAt()).isNotNull();
    assertThat(loaded.getUpdatedAt()).isNotNull();
    assertThat(loaded.getTags())
      .extracting(Tag::getSlug)
      .containsExactly("java");
    assertThat(loaded.getTags().iterator().next().nameIn(SupportedLocale.EN))
      .contains("Java");
    assertThat(loaded.translation(SupportedLocale.EN)).isPresent();
  }

  private Tag saveTag(String slug, String enName) {
    Tag tag = new Tag(slug, null);
    if (enName != null) {
      tag.putName(SupportedLocale.EN, enName);
    }
    return tags.saveAndFlush(tag);
  }

  @Test
  void tagsVisibleInLocaleOnlyIncludesTagsOfVisiblePosts() {
    Tag used = saveTag("b-used", "Used");
    Tag alsoUsed = saveTag("a-also", "Also");
    Tag onlyDraft = saveTag("draft-only", "Draft");
    Tag onlyDeleted = saveTag("deleted-only", "Deleted");
    Tag onlyUk = saveTag("uk-only", "Uk");
    saveTag("unused", "Unused");

    Post p1 = savePost("p1", SupportedLocale.EN, 1);
    p1.replaceTags(Set.of(used, alsoUsed));
    Post p2 = savePost("p2", SupportedLocale.EN, 2);
    p2.replaceTags(Set.of(used));
    Post draft = new Post("p3");
    draft.putTranslation(SupportedLocale.EN, content("p3"));
    draft.replaceTags(Set.of(onlyDraft));
    posts.save(draft);
    Post deleted = savePost("p4", SupportedLocale.EN, 3);
    deleted.replaceTags(Set.of(onlyDeleted));
    deleted.softDelete(BASE);
    Post uk = savePost("p5", SupportedLocale.UK, 4);
    uk.replaceTags(Set.of(onlyUk));
    clear();

    List<Tag> en = tags.findUsedByPostsVisibleIn(SupportedLocale.EN);

    assertThat(en).extracting(Tag::getSlug).containsExactly("a-also", "b-used");
    assertThat(tags.findUsedByPostsVisibleIn(SupportedLocale.UK))
      .extracting(Tag::getSlug)
      .containsExactly("uk-only");
    assertThat(tags.findUsedByPostsVisibleIn(SupportedLocale.JA)).isEmpty();
  }

  @Test
  void countsPostsUsingTagAndLooksUpBySlug() {
    Tag tag = saveTag("counted", "Counted");
    saveTag("other", "Other");
    savePost("c1", SupportedLocale.EN, 1).replaceTags(Set.of(tag));
    Post c2 = savePost("c2", SupportedLocale.EN, 2);
    c2.replaceTags(Set.of(tag));
    c2.softDelete(BASE);
    clear();

    assertThat(tags.countPostsUsing(tag.getId())).isEqualTo(2);
    assertThat(tags.existsBySlug("counted")).isTrue();
    assertThat(tags.findBySlug("counted")).isPresent();
    assertThat(tags.findBySlugIn(List.of("counted", "other", "nope")))
      .extracting(Tag::getSlug)
      .containsExactlyInAnyOrder("counted", "other");
  }

  @Test
  void duplicatePostSlugIsRejected() {
    posts.saveAndFlush(new Post("dup"));

    assertThatThrownBy(() -> posts.saveAndFlush(new Post("dup")))
      .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void duplicateTagNamePerLocaleIsRejectedButAllowedAcrossLocales() {
    saveTag("one", "Same");
    Tag other = saveTag("other-locale", null);
    other.putName(SupportedLocale.UK, "Same");
    tags.saveAndFlush(other);

    Tag dup = new Tag("two", null);
    dup.putName(SupportedLocale.EN, "Same");

    assertThatThrownBy(() -> tags.saveAndFlush(dup))
      .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void sortInPageableIsIgnoredAndPublishedAtDescKept() {
    savePost("old", SupportedLocale.EN, 1);
    savePost("new", SupportedLocale.EN, 3);
    savePost("middle", SupportedLocale.EN, 2);
    clear();

    Page<PostTranslation> page = translations.findVisibleIn(
      SupportedLocale.EN,
      PageRequest.of(0, 10, Sort.by("nonexistent").ascending())
    );

    assertThat(page.getContent())
      .extracting(t -> t.getPost().getSlug())
      .containsExactly("new", "middle", "old");
  }

  private Instant postUpdatedAt(Long id) {
    return jdbc
      .queryForObject(
        "SELECT updated_at FROM posts WHERE id = ?",
        java.sql.Timestamp.class,
        id
      )
      .toInstant();
  }

  @Test
  void translationEditAdvancesPostUpdatedAt() throws InterruptedException {
    Post post = posts.saveAndFlush(new Post("touch-translation"));
    post.putTranslation(SupportedLocale.EN, content("v1"));
    posts.saveAndFlush(post);
    Instant before = postUpdatedAt(post.getId());
    clear();
    Thread.sleep(20);

    Post loaded = posts.findById(post.getId()).orElseThrow();
    loaded.putTranslation(SupportedLocale.EN, content("v2"));
    em.flush();

    assertThat(postUpdatedAt(post.getId())).isAfter(before);
  }

  @Test
  void tagEditAdvancesPostUpdatedAt() throws InterruptedException {
    Tag tag = saveTag("touch-tag", "Touch");
    Post post = posts.saveAndFlush(new Post("touch-tags"));
    Instant before = postUpdatedAt(post.getId());
    clear();
    Thread.sleep(20);

    Post loaded = posts.findById(post.getId()).orElseThrow();
    loaded.replaceTags(Set.of(tags.findById(tag.getId()).orElseThrow()));
    em.flush();

    assertThat(postUpdatedAt(post.getId())).isAfter(before);
  }
}
