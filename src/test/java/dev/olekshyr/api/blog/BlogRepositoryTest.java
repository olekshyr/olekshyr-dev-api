package dev.olekshyr.api.blog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.entry;

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
import org.springframework.data.domain.Limit;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
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
    post.putTranslation(locale, content(slug), BASE);
    post.publish(locale, BASE.plusSeconds(daysOffset * 86400L));
    return posts.saveAndFlush(post);
  }

  private void clear() {
    em.flush();
    em.clear();
  }

  private Page<Long> visibleIds(
    SupportedLocale locale,
    String tag,
    PostOrder order,
    PageRequest page
  ) {
    return translations.findVisiblePostIds(locale, tag, order, page);
  }

  private List<String> slugs(Page<Long> ids) {
    return ids
      .getContent()
      .stream()
      .map(id -> posts.findById(id).orElseThrow().getSlug())
      .toList();
  }

  private List<String> visibleSlugs(
    SupportedLocale locale,
    String tag,
    PostOrder order
  ) {
    return slugs(visibleIds(locale, tag, order, PageRequest.of(0, 50)));
  }

  /** A translation in another locale, published at the given day offset. */
  private void addTranslation(Post post, SupportedLocale locale, int days) {
    post.putTranslation(locale, content(post.getSlug() + "-" + locale), BASE);
    post.publish(locale, BASE.plusSeconds(days * 86400L));
    posts.saveAndFlush(post);
  }

  @Test
  void publicListReturnsOnlyPublishedInLocaleNewestFirst() {
    savePost("old", SupportedLocale.EN, 1);
    savePost("new", SupportedLocale.EN, 3);
    savePost("middle", SupportedLocale.EN, 2);
    savePost("ukrainian", SupportedLocale.UK, 4);
    Post draft = new Post("draft");
    draft.putTranslation(SupportedLocale.EN, content("draft"), BASE);
    posts.save(draft);
    Post unpublished = savePost("unpublished", SupportedLocale.EN, 5);
    unpublished.unpublish(SupportedLocale.EN, BASE);
    clear();

    Page<Long> page = visibleIds(
      SupportedLocale.EN,
      null,
      PostOrder.NEWEST,
      PageRequest.of(0, 10)
    );

    assertThat(slugs(page)).containsExactly("new", "middle", "old");
    assertThat(page.getTotalElements()).isEqualTo(3);
  }

  @Test
  void publicListExcludesSoftDeletedPosts() {
    savePost("kept", SupportedLocale.EN, 1);
    Post deleted = savePost("deleted", SupportedLocale.EN, 2);
    deleted.softDelete(BASE);
    clear();

    assertThat(visibleSlugs(SupportedLocale.EN, null, PostOrder.NEWEST))
      .containsExactly("kept");
    assertThat(visibleSlugs(null, null, PostOrder.NEWEST))
      .containsExactly("kept");
  }

  @Test
  void publicListPaginatesWithCorrectCounts() {
    for (int i = 1; i <= 5; i++) {
      savePost("post-" + i, SupportedLocale.EN, i);
    }
    clear();

    Page<Long> second = visibleIds(
      SupportedLocale.EN,
      null,
      PostOrder.NEWEST,
      PageRequest.of(1, 2)
    );

    assertThat(second.getTotalElements()).isEqualTo(5);
    assertThat(second.getTotalPages()).isEqualTo(3);
    assertThat(slugs(second)).containsExactly("post-3", "post-2");
  }

  @Test
  void oldestFirstIsTheExactReverse_tiesBySlug() {
    savePost("b-tie", SupportedLocale.EN, 2);
    savePost("a-tie", SupportedLocale.EN, 2);
    savePost("first", SupportedLocale.EN, 1);
    savePost("last", SupportedLocale.EN, 3);
    clear();

    assertThat(visibleSlugs(SupportedLocale.EN, null, PostOrder.NEWEST))
      .containsExactly("last", "b-tie", "a-tie", "first");
    assertThat(visibleSlugs(SupportedLocale.EN, null, PostOrder.OLDEST))
      .containsExactly("first", "a-tie", "b-tie", "last");
  }

  @Test
  void anyLocaleList_countsEachPostOnce_sortedByEarliestPublish() {
    // en on day 5, uk on day 1: sorts by day 1 in every locale
    Post both = savePost("both", SupportedLocale.EN, 5);
    addTranslation(both, SupportedLocale.UK, 1);
    savePost("english", SupportedLocale.EN, 3);
    savePost("japanese", SupportedLocale.JA, 2);
    Post draft = new Post("draft");
    draft.putTranslation(SupportedLocale.EN, content("draft"), BASE);
    posts.save(draft);
    clear();

    Page<Long> page = visibleIds(
      null,
      null,
      PostOrder.NEWEST,
      PageRequest.of(0, 10)
    );

    assertThat(slugs(page)).containsExactly("english", "japanese", "both");
    assertThat(page.getTotalElements()).isEqualTo(3);
    assertThat(visibleSlugs(SupportedLocale.EN, null, PostOrder.NEWEST))
      .containsExactly("both", "english");
  }

  @Test
  void tagFilter_keepsOnlyPostsWithTheTag() {
    Tag java = saveTag("java", "Java");
    Tag life = saveTag("life", "Life");
    savePost("p1", SupportedLocale.EN, 1).replaceTags(Set.of(java), BASE);
    savePost("p2", SupportedLocale.EN, 2).replaceTags(Set.of(java, life), BASE);
    savePost("p3", SupportedLocale.EN, 3).replaceTags(Set.of(life), BASE);
    savePost("p4", SupportedLocale.UK, 4).replaceTags(Set.of(java), BASE);
    clear();

    Page<Long> page = visibleIds(
      SupportedLocale.EN,
      "java",
      PostOrder.NEWEST,
      PageRequest.of(0, 10)
    );

    assertThat(slugs(page)).containsExactly("p2", "p1");
    assertThat(page.getTotalElements()).isEqualTo(2);
    assertThat(visibleSlugs(null, "java", PostOrder.OLDEST))
      .containsExactly("p1", "p2", "p4");
    assertThat(
      visibleSlugs(SupportedLocale.EN, "no-such-tag", PostOrder.NEWEST)
    )
      .isEmpty();
  }

  @Test
  void neighbours_followTheListOrderInTheLocale() {
    savePost("first", SupportedLocale.EN, 1);
    savePost("a-tie", SupportedLocale.EN, 2);
    savePost("b-tie", SupportedLocale.EN, 2);
    savePost("ukrainian", SupportedLocale.UK, 3);
    savePost("last", SupportedLocale.EN, 4);
    clear();
    Instant day2 = BASE.plusSeconds(2 * 86400L);

    assertThat(
      translations.findVisibleBefore(
        SupportedLocale.EN,
        day2,
        "b-tie",
        Limit.of(1)
      )
    )
      .extracting(t -> t.getPost().getSlug())
      .containsExactly("a-tie");
    assertThat(
      translations.findVisibleAfter(
        SupportedLocale.EN,
        day2,
        "b-tie",
        Limit.of(1)
      )
    )
      .extracting(t -> t.getPost().getSlug())
      .containsExactly("last");
    assertThat(
      translations.findVisibleBefore(
        SupportedLocale.EN,
        BASE.plusSeconds(86400L),
        "first",
        Limit.of(1)
      )
    )
      .isEmpty();
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
    post.putTranslation(SupportedLocale.EN, content("en"), BASE);
    post.putTranslation(SupportedLocale.UK, content("uk"), BASE);
    post.putTranslation(SupportedLocale.JA, content("ja"), BASE);
    post.publish(SupportedLocale.EN, BASE);
    post.publish(SupportedLocale.JA, BASE);
    posts.saveAndFlush(post);
    clear();

    assertThat(translations.findVisibleLocalesBySlug("multi"))
      .containsExactlyInAnyOrder(SupportedLocale.EN, SupportedLocale.JA);
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
    post.putTranslation(SupportedLocale.EN, content("t"), BASE);
    post.replaceTags(Set.of(tag), BASE);
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
  void tagCountsOnlyCountVisiblePosts() {
    Tag used = saveTag("b-used", "Used");
    Tag alsoUsed = saveTag("a-also", "Also");
    Tag onlyDraft = saveTag("draft-only", "Draft");
    Tag onlyDeleted = saveTag("deleted-only", "Deleted");
    Tag onlyUk = saveTag("uk-only", "Uk");
    saveTag("unused", "Unused");

    Post p1 = savePost("p1", SupportedLocale.EN, 1);
    p1.replaceTags(Set.of(used, alsoUsed), BASE);
    Post p2 = savePost("p2", SupportedLocale.EN, 2);
    p2.replaceTags(Set.of(used), BASE);
    Post draft = new Post("p3");
    draft.putTranslation(SupportedLocale.EN, content("p3"), BASE);
    draft.replaceTags(Set.of(onlyDraft), BASE);
    posts.save(draft);
    Post deleted = savePost("p4", SupportedLocale.EN, 3);
    deleted.replaceTags(Set.of(onlyDeleted), BASE);
    deleted.softDelete(BASE);
    Post uk = savePost("p5", SupportedLocale.UK, 4);
    uk.replaceTags(Set.of(onlyUk), BASE);
    clear();

    assertThat(translations.countVisiblePostsPerTag(SupportedLocale.EN))
      .containsOnly(entry(used.getId(), 2L), entry(alsoUsed.getId(), 1L));
    assertThat(translations.countVisiblePostsPerTag(SupportedLocale.UK))
      .containsOnly(entry(onlyUk.getId(), 1L));
    assertThat(translations.countVisiblePostsPerTag(SupportedLocale.JA))
      .isEmpty();
    assertThat(translations.countVisiblePostsPerTag(null))
      .containsOnly(
        entry(used.getId(), 2L),
        entry(alsoUsed.getId(), 1L),
        entry(onlyUk.getId(), 1L)
      );
  }

  @Test
  void tagCountsCountAPostOnceAcrossItsLocales() {
    Tag tag = saveTag("multi", "Multi");
    Post post = savePost("two-locales", SupportedLocale.EN, 1);
    addTranslation(post, SupportedLocale.UK, 2);
    post.replaceTags(Set.of(tag), BASE);
    posts.saveAndFlush(post);
    clear();

    assertThat(translations.countVisiblePostsPerTag(null))
      .containsOnly(entry(tag.getId(), 1L));
  }

  @Test
  void countsPostsUsingTagAndLooksUpBySlug() {
    Tag tag = saveTag("counted", "Counted");
    saveTag("other", "Other");
    savePost("c1", SupportedLocale.EN, 1).replaceTags(Set.of(tag), BASE);
    Post c2 = savePost("c2", SupportedLocale.EN, 2);
    c2.replaceTags(Set.of(tag), BASE);
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
  void duplicateTagSlugIsRejected() {
    saveTag("dup", null);

    assertThatThrownBy(() -> tags.saveAndFlush(new Tag("dup", null)))
      .isInstanceOf(DataIntegrityViolationException.class)
      .hasMessageContaining("tags_slug_key");
  }

  @Test
  void secondTranslationInSameLocaleIsRejected() {
    Post post = savePost("one-per-locale", SupportedLocale.EN, 1);
    clear();

    assertThatThrownBy(() ->
        jdbc.update(
          "INSERT INTO post_translations (post_id, locale, title, body) " +
          "VALUES (?, 'en', 'Again', 'Body')",
          post.getId()
        )
      )
      .isInstanceOf(DataIntegrityViolationException.class)
      .hasMessageContaining("uq_post_locale");
  }

  @Test
  void localeIsStoredLowercaseAndStatusUppercase() {
    savePost("casing", SupportedLocale.UK, 1);
    clear();

    assertThat(
      jdbc.queryForMap(
        "SELECT t.locale, t.status FROM post_translations t " +
        "JOIN posts p ON p.id = t.post_id WHERE p.slug = 'casing'"
      )
    )
      .containsEntry("locale", "uk")
      .containsEntry("status", "PUBLISHED");
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
    post.putTranslation(SupportedLocale.EN, content("v1"), BASE);
    posts.saveAndFlush(post);
    Instant before = postUpdatedAt(post.getId());
    clear();
    Thread.sleep(20);

    Post loaded = posts.findById(post.getId()).orElseThrow();
    loaded.putTranslation(SupportedLocale.EN, content("v2"), BASE);
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
    loaded.replaceTags(Set.of(tags.findById(tag.getId()).orElseThrow()), BASE);
    em.flush();

    assertThat(postUpdatedAt(post.getId())).isAfter(before);
  }
}
