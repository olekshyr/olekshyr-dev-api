package dev.olekshyr.api.blog;

import dev.olekshyr.api.shared.SupportedLocale;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.BatchSize;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Entity
@Table(name = "tags")
@EntityListeners(AuditingEntityListener.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class Tag {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, unique = true)
  private String slug;

  @Column(length = 7)
  private String color;

  @CreatedDate
  @Column(nullable = false, updatable = false)
  private Instant createdAt;

  @LastModifiedDate
  @Column(nullable = false)
  private Instant updatedAt;

  @OneToMany(mappedBy = "tag", cascade = CascadeType.ALL, orphanRemoval = true)
  @BatchSize(size = 50)
  @Getter(AccessLevel.NONE)
  private List<TagTranslation> translations = new ArrayList<>();

  Tag(String slug, String color) {
    this.slug = slug;
    this.color = color;
  }

  void changeSlug(String slug) {
    this.slug = slug;
  }

  void changeColor(String color) {
    this.color = color;
  }

  /**
   * Makes the names exactly the given ones. Existing rows are renamed in
   * place, never removed and re-added: Hibernate flushes inserts before
   * deletes, so a re-added row would hit uq_tag_locale.
   */
  void replaceNames(Map<SupportedLocale, String> names, Instant now) {
    translations.removeIf(t -> !names.containsKey(t.getLocale()));
    names.forEach(this::putName);
    touch(now);
  }

  Map<SupportedLocale, String> names() {
    Map<SupportedLocale, String> names = new EnumMap<>(SupportedLocale.class);
    translations.forEach(t -> names.put(t.getLocale(), t.getName()));
    return names;
  }

  /** Sets (creates or replaces) the name of this tag in the given locale. */
  void putName(SupportedLocale locale, String name) {
    translation(locale)
      .ifPresentOrElse(
        t -> t.rename(name),
        () -> {
          translations.add(new TagTranslation(this, locale, name));
        }
      );
  }

  Optional<String> nameIn(SupportedLocale locale) {
    return translation(locale).map(TagTranslation::getName);
  }

  /**
   * Marks the tags row dirty when only names changed, so auditing runs on
   * flush and replaces this value with the application clock's time.
   */
  private void touch(Instant now) {
    this.updatedAt = now;
  }

  private Optional<TagTranslation> translation(SupportedLocale locale) {
    return translations
      .stream()
      .filter(t -> t.getLocale() == locale)
      .findFirst();
  }
}
