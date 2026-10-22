package dev.olekshyr.api.blog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.olekshyr.api.shared.FieldViolation;
import dev.olekshyr.api.shared.SupportedLocale;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import org.junit.jupiter.api.Test;

class PostLifecycleTest {

  private static final Instant T1 = Instant.parse("2026-01-01T10:00:00Z");
  private static final Instant T2 = Instant.parse("2026-02-01T10:00:00Z");
  private static final Predicate<String> NOTHING_TAKEN = slug -> false;
  private static final Predicate<String> EVERYTHING_TAKEN = slug -> true;

  private static PostContent complete() {
    return new PostContent("Title", "Excerpt", "Body", "Meta", "Meta desc");
  }

  private static Post postWith(SupportedLocale locale, PostContent content) {
    Post post = new Post("my-post");
    post.putTranslation(locale, content, T1);
    return post;
  }

  @Test
  void publishSetsStatusAndPublishedAt() {
    Post post = postWith(SupportedLocale.EN, complete());

    post.publish(SupportedLocale.EN, T1);

    PostTranslation t = post.translation(SupportedLocale.EN).orElseThrow();
    assertThat(t.getStatus()).isEqualTo(PostStatus.PUBLISHED);
    assertThat(t.getPublishedAt()).isEqualTo(T1);
  }

  @Test
  void republishAfterUnpublishKeepsOriginalPublishedAt() {
    Post post = postWith(SupportedLocale.EN, complete());
    post.publish(SupportedLocale.EN, T1);

    post.unpublish(SupportedLocale.EN, T1);
    post.publish(SupportedLocale.EN, T2);

    PostTranslation t = post.translation(SupportedLocale.EN).orElseThrow();
    assertThat(t.getStatus()).isEqualTo(PostStatus.PUBLISHED);
    assertThat(t.getPublishedAt()).isEqualTo(T1);
  }

  @Test
  void unpublishRevertsToDraftAndKeepsPublishedAt() {
    Post post = postWith(SupportedLocale.EN, complete());
    post.publish(SupportedLocale.EN, T1);

    post.unpublish(SupportedLocale.EN, T1);

    PostTranslation t = post.translation(SupportedLocale.EN).orElseThrow();
    assertThat(t.getStatus()).isEqualTo(PostStatus.DRAFT);
    assertThat(t.getPublishedAt()).isEqualTo(T1);
  }

  @Test
  void publishWithoutExcerptAndMetaDescriptionReportsBothFields() {
    Post post = postWith(
      SupportedLocale.EN,
      new PostContent("Title", " ", "Body", null, null)
    );

    assertThatThrownBy(() -> post.publish(SupportedLocale.EN, T1))
      .isInstanceOfSatisfying(
        PublishRequirementsNotMetException.class,
        e ->
          assertThat(e.violations())
            .extracting(FieldViolation::field)
            .containsExactly("excerpt", "metaDescription")
      );
    assertThat(
      post.translation(SupportedLocale.EN).orElseThrow().getPublishedAt()
    )
      .isNull();
  }

  @Test
  void publishWithoutMetaDescriptionReportsOnlyThatField() {
    Post post = postWith(
      SupportedLocale.EN,
      new PostContent("Title", "Excerpt", "Body", null, "")
    );

    assertThatThrownBy(() -> post.publish(SupportedLocale.EN, T1))
      .isInstanceOfSatisfying(
        PublishRequirementsNotMetException.class,
        e ->
          assertThat(e.violations())
            .extracting(FieldViolation::field)
            .containsExactly("metaDescription")
      );
  }

  @Test
  void publishMissingTranslationFails() {
    Post post = postWith(SupportedLocale.EN, complete());

    assertThatThrownBy(() -> post.publish(SupportedLocale.UK, T1))
      .isInstanceOf(TranslationNotFoundException.class);
  }

  @Test
  void changeSlugAllowedBeforeFirstPublish() {
    Post post = postWith(SupportedLocale.EN, complete());

    post.changeSlug("new-slug", NOTHING_TAKEN);

    assertThat(post.getSlug()).isEqualTo("new-slug");
  }

  @Test
  void changeSlugFrozenAfterFirstPublishEvenIfUnpublished() {
    Post post = postWith(SupportedLocale.EN, complete());
    post.publish(SupportedLocale.EN, T1);
    post.unpublish(SupportedLocale.EN, T1);

    assertThat(post.isEverPublished()).isTrue();
    assertThatThrownBy(() -> post.changeSlug("new-slug", NOTHING_TAKEN))
      .isInstanceOf(SlugFrozenException.class);
    assertThat(post.getSlug()).isEqualTo("my-post");
  }

  @Test
  void changeSlugToTakenSlugFails() {
    Post post = postWith(SupportedLocale.EN, complete());

    assertThatThrownBy(() -> post.changeSlug("taken", EVERYTHING_TAKEN))
      .isInstanceOf(DuplicateSlugException.class);
    assertThat(post.getSlug()).isEqualTo("my-post");
  }

  @Test
  void frozenSlugIsReportedBeforeTakenSlug() {
    Post post = postWith(SupportedLocale.EN, complete());
    post.publish(SupportedLocale.EN, T1);

    assertThatThrownBy(() -> post.changeSlug("taken", EVERYTHING_TAKEN))
      .isInstanceOf(SlugFrozenException.class);
  }

  @Test
  void changeSlugToSameSlugIsANoOpEvenWhenFrozen() {
    Post post = postWith(SupportedLocale.EN, complete());
    post.publish(SupportedLocale.EN, T1);

    post.changeSlug("my-post", NOTHING_TAKEN);

    assertThat(post.getSlug()).isEqualTo("my-post");
  }

  @Test
  void putTranslationReplacesExistingContent() {
    Post post = postWith(SupportedLocale.EN, complete());

    PostTranslation updated = post.putTranslation(
      SupportedLocale.EN,
      new PostContent("New", "E", "B", null, "D"),
      T1
    );

    assertThat(post.getTranslations()).hasSize(1).containsExactly(updated);
    assertThat(updated.getTitle()).isEqualTo("New");
  }

  @Test
  void softDeleteMarksPostDeleted() {
    Post post = new Post("p");
    assertThat(post.isDeleted()).isFalse();

    post.softDelete(T1);

    assertThat(post.isDeleted()).isTrue();
    assertThat(post.getDeletedAt()).isEqualTo(T1);
  }

  @Test
  void softDeleteAgainKeepsFirstDeletedAt() {
    Post post = new Post("p");
    post.softDelete(T1);

    post.softDelete(T2);

    assertThat(post.getDeletedAt()).isEqualTo(T1);
  }

  @Test
  void replaceTagsSwapsTheSet() {
    Post post = new Post("p");
    Tag a = new Tag("a", null);
    Tag b = new Tag("b", null);
    post.replaceTags(Set.of(a, b), T1);

    post.replaceTags(Set.of(b), T1);

    assertThat(post.getTags()).containsExactly(b);
  }

  @Test
  void tagNameIsPerLocaleAndReplaceable() {
    Tag tag = new Tag("java", "#ff0000");
    tag.putName(SupportedLocale.EN, "Java");
    tag.putName(SupportedLocale.EN, "Java!");

    assertThat(tag.nameIn(SupportedLocale.EN)).contains("Java!");
    assertThat(tag.nameIn(SupportedLocale.UK)).isEmpty();
  }

  @Test
  void replaceNamesRenamesAddsAndRemoves() {
    Tag tag = new Tag("java", null);
    tag.putName(SupportedLocale.EN, "Java");
    tag.putName(SupportedLocale.UK, "Джава");

    tag.replaceNames(
      Map.of(SupportedLocale.EN, "Java SE", SupportedLocale.JA, "ジャバ"),
      T1
    );

    assertThat(tag.names())
      .containsExactly(
        Map.entry(SupportedLocale.EN, "Java SE"),
        Map.entry(SupportedLocale.JA, "ジャバ")
      );
  }

  @Test
  void deletedPostRejectsAllMutations() {
    Post post = postWith(SupportedLocale.EN, complete());
    post.softDelete(T1);

    assertThatThrownBy(() -> post.changeSlug("other", NOTHING_TAKEN))
      .isInstanceOf(PostDeletedException.class);
    assertThatThrownBy(() ->
        post.putTranslation(SupportedLocale.UK, complete(), T1)
      )
      .isInstanceOf(PostDeletedException.class);
    assertThatThrownBy(() -> post.publish(SupportedLocale.EN, T2))
      .isInstanceOf(PostDeletedException.class);
    assertThatThrownBy(() -> post.unpublish(SupportedLocale.EN, T1))
      .isInstanceOf(PostDeletedException.class);
    assertThatThrownBy(() -> post.replaceTags(Set.of(), T1))
      .isInstanceOf(PostDeletedException.class);
    assertThat(post.getSlug()).isEqualTo("my-post");
  }
}
