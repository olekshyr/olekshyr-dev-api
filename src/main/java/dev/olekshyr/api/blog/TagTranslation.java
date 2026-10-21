package dev.olekshyr.api.blog;

import dev.olekshyr.api.shared.SupportedLocale;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
  name = "tag_translations",
  uniqueConstraints = {
    @UniqueConstraint(columnNames = { "tag_id", "locale" }),
    @UniqueConstraint(columnNames = { "locale", "name" }),
  }
)
@Getter(AccessLevel.PACKAGE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class TagTranslation {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "tag_id", nullable = false)
  private Tag tag;

  @Column(nullable = false, length = 5)
  private SupportedLocale locale;

  @Column(nullable = false, length = 100)
  private String name;

  TagTranslation(Tag tag, SupportedLocale locale, String name) {
    this.tag = tag;
    this.locale = locale;
    this.name = name;
  }

  void rename(String name) {
    this.name = name;
  }
}
