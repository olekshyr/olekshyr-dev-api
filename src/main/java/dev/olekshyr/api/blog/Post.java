package dev.olekshyr.api.blog;

import dev.olekshyr.api.shared.SupportedLocale;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Predicate;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.BatchSize;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Entity
@Table(name = "posts")
@EntityListeners(AuditingEntityListener.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class Post {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, unique = true)
  private String slug;

  @CreatedDate
  @Column(nullable = false, updatable = false)
  private Instant createdAt;

  @LastModifiedDate
  @Column(nullable = false)
  private Instant updatedAt;

  private Instant deletedAt;

  @OneToMany(mappedBy = "post", cascade = CascadeType.ALL, orphanRemoval = true)
  @BatchSize(size = 50)
  @Getter(AccessLevel.NONE)
  private List<PostTranslation> translations = new ArrayList<>();

  @ManyToMany
  @JoinTable(
    name = "post_tags",
    joinColumns = @JoinColumn(name = "post_id"),
    inverseJoinColumns = @JoinColumn(name = "tag_id")
  )
  @BatchSize(size = 50)
  @Getter(AccessLevel.NONE)
  private Set<Tag> tags = new HashSet<>();

  Post(String slug) {
    this.slug = slug;
  }

  List<PostTranslation> getTranslations() {
    return Collections.unmodifiableList(translations);
  }

  /** In en, uk, ja order (D18). */
  List<PostTranslation> translationsInLocaleOrder() {
    return translations
      .stream()
      .sorted(Comparator.comparing(PostTranslation::getLocale))
      .toList();
  }

  Set<Tag> getTags() {
    return Collections.unmodifiableSet(tags);
  }

  Optional<PostTranslation> translation(SupportedLocale locale) {
    return translations
      .stream()
      .filter(t -> t.getLocale() == locale)
      .findFirst();
  }

  /** Creates or replaces the content of the translation in the given locale. */
  PostTranslation putTranslation(
    SupportedLocale locale,
    PostContent content,
    Instant now
  ) {
    requireNotDeleted();
    PostTranslation translation = translation(locale)
      .orElseGet(() -> {
        PostTranslation created = new PostTranslation(this, locale);
        translations.add(created);
        return created;
      });
    translation.updateContent(content);
    touch(now);
    return translation;
  }

  void replaceTags(Set<Tag> newTags, Instant now) {
    requireNotDeleted();
    tags.retainAll(newTags);
    tags.addAll(newTags);
    touch(now);
  }

  void publish(SupportedLocale locale, Instant now) {
    requireNotDeleted();
    PostTranslation translation = translation(locale)
      .orElseThrow(() -> new TranslationNotFoundException(locale));
    Set<String> missing = new TreeSet<>();
    if (isBlank(translation.getExcerpt())) {
      missing.add("excerpt");
    }
    if (isBlank(translation.getMetaDescription())) {
      missing.add("metaDescription");
    }
    if (!missing.isEmpty()) {
      throw new PublishRequirementsNotMetException(missing);
    }
    translation.markPublished(now);
    touch(now);
  }

  void unpublish(SupportedLocale locale, Instant now) {
    requireNotDeleted();
    translation(locale)
      .orElseThrow(() -> new TranslationNotFoundException(locale))
      .markDraft();
    touch(now);
  }

  boolean isEverPublished() {
    return translations.stream().anyMatch(t -> t.getPublishedAt() != null);
  }

  /**
   * The slug rules (D10), in order: saving the slug the post already has is a
   * no-op, a published post's slug is frozen, and a new slug must not be taken.
   */
  void changeSlug(String newSlug, Predicate<String> isTaken) {
    requireNotDeleted();
    if (slug.equals(newSlug)) {
      return;
    }
    if (isEverPublished()) {
      throw new SlugFrozenException(slug);
    }
    if (isTaken.test(newSlug)) {
      throw new DuplicateSlugException(newSlug);
    }
    this.slug = newSlug;
  }

  /** Idempotent: deleting again keeps the first deletedAt (D23). */
  void softDelete(Instant now) {
    if (deletedAt == null) {
      this.deletedAt = now;
    }
  }

  boolean isDeleted() {
    return deletedAt != null;
  }

  private void requireNotDeleted() {
    if (isDeleted()) {
      throw new PostDeletedException(slug);
    }
  }

  /**
   * Marks the posts row dirty when only translations or tags changed, so
   * auditing runs on flush. The value only lasts until then: auditing's
   * {@code @PreUpdate} replaces it with the application clock's time, which is
   * what gets stored.
   */
  private void touch(Instant now) {
    this.updatedAt = now;
  }

  private static boolean isBlank(String value) {
    return value == null || value.isBlank();
  }
}
