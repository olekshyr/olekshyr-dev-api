package dev.olekshyr.api.blog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.Set;
import org.junit.jupiter.api.Test;

class PostLifecycleTest {

  private static final Instant T1 = Instant.parse("2026-01-01T10:00:00Z");
  private static final Instant T2 = Instant.parse("2026-02-01T10:00:00Z");

  private static PostContent complete() {
    return new PostContent("Title", "Excerpt", "Body", "Meta", "Meta desc");
  }

  private static Post postWith(SupportedLocale locale, PostContent content) {
    Post post = new Post("my-post");
    post.putTranslation(locale, content);
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

    post.unpublish(SupportedLocale.EN);
    post.publish(SupportedLocale.EN, T2);

    PostTranslation t = post.translation(SupportedLocale.EN).orElseThrow();
    assertThat(t.getStatus()).isEqualTo(PostStatus.PUBLISHED);
    assertThat(t.getPublishedAt()).isEqualTo(T1);
  }

  @Test
  void unpublishRevertsToDraftAndKeepsPublishedAt() {
    Post post = postWith(SupportedLocale.EN, complete());
    post.publish(SupportedLocale.EN, T1);

    post.unpublish(SupportedLocale.EN);

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
          assertThat(e.getMissingFields())
            .isEqualTo(Set.of("excerpt", "metaDescription"))
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
        e -> assertThat(e.getMissingFields()).containsExactly("metaDescription")
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

    post.changeSlug("new-slug");

    assertThat(post.getSlug()).isEqualTo("new-slug");
  }

  @Test
  void changeSlugFrozenAfterFirstPublishEvenIfUnpublished() {
    Post post = postWith(SupportedLocale.EN, complete());
    post.publish(SupportedLocale.EN, T1);
    post.unpublish(SupportedLocale.EN);

    assertThat(post.isEverPublished()).isTrue();
    assertThatThrownBy(() -> post.changeSlug("new-slug"))
      .isInstanceOf(SlugFrozenException.class);
    assertThat(post.getSlug()).isEqualTo("my-post");
  }

  @Test
  void putTranslationReplacesExistingContent() {
    Post post = postWith(SupportedLocale.EN, complete());

    PostTranslation updated = post.putTranslation(
      SupportedLocale.EN,
      new PostContent("New", "E", "B", null, "D")
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
  void replaceTagsSwapsTheSet() {
    Post post = new Post("p");
    Tag a = new Tag("a", null);
    Tag b = new Tag("b", null);
    post.replaceTags(Set.of(a, b));

    post.replaceTags(Set.of(b));

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
  void deletedPostRejectsAllMutations() {
    Post post = postWith(SupportedLocale.EN, complete());
    post.softDelete(T1);

    assertThatThrownBy(() -> post.changeSlug("other"))
      .isInstanceOf(PostDeletedException.class);
    assertThatThrownBy(() -> post.putTranslation(SupportedLocale.UK, complete())
      )
      .isInstanceOf(PostDeletedException.class);
    assertThatThrownBy(() -> post.publish(SupportedLocale.EN, T2))
      .isInstanceOf(PostDeletedException.class);
    assertThatThrownBy(() -> post.unpublish(SupportedLocale.EN))
      .isInstanceOf(PostDeletedException.class);
    assertThatThrownBy(() -> post.replaceTags(Set.of()))
      .isInstanceOf(PostDeletedException.class);
    assertThat(post.getSlug()).isEqualTo("my-post");
  }
}
