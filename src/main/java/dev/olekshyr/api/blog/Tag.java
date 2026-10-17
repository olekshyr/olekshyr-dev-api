package dev.olekshyr.api.blog;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
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

  void changeColor(String color) {
    this.color = color;
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

  private Optional<TagTranslation> translation(SupportedLocale locale) {
    return translations
      .stream()
      .filter(t -> t.getLocale() == locale)
      .findFirst();
  }
}
