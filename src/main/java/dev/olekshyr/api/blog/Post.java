package dev.olekshyr.api.blog;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
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
  PostTranslation putTranslation(SupportedLocale locale, PostContent content) {
    requireNotDeleted();
    PostTranslation translation = translation(locale)
      .orElseGet(() -> {
        PostTranslation created = new PostTranslation(this, locale);
        translations.add(created);
        return created;
      });
    translation.updateContent(content);
    touch();
    return translation;
  }

  void replaceTags(Set<Tag> newTags) {
    requireNotDeleted();
    tags.retainAll(newTags);
    tags.addAll(newTags);
    touch();
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
    touch();
  }

  void unpublish(SupportedLocale locale) {
    requireNotDeleted();
    translation(locale)
      .orElseThrow(() -> new TranslationNotFoundException(locale))
      .markDraft();
    touch();
  }

  boolean isEverPublished() {
    return translations.stream().anyMatch(t -> t.getPublishedAt() != null);
  }

  void changeSlug(String newSlug) {
    requireNotDeleted();
    if (isEverPublished()) {
      throw new SlugFrozenException(slug);
    }
    this.slug = newSlug;
  }

  void softDelete(Instant now) {
    this.deletedAt = now;
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
   * Dirties the posts row so auditing refreshes updatedAt when only
   * translations or tags changed.
   */
  private void touch() {
    this.updatedAt = Instant.now();
  }

  private static boolean isBlank(String value) {
    return value == null || value.isBlank();
  }
}
