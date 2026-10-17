package dev.olekshyr.api.blog;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Entity
@Table(
  name = "post_translations",
  uniqueConstraints = @UniqueConstraint(columnNames = { "post_id", "locale" })
)
@EntityListeners(AuditingEntityListener.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class PostTranslation {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "post_id", nullable = false)
  private Post post;

  @Column(nullable = false, length = 5)
  private SupportedLocale locale;

  @Column(nullable = false)
  private String title;

  @Column(length = 500)
  private String excerpt;

  @Column(nullable = false, columnDefinition = "TEXT")
  private String body;

  private String metaTitle;

  @Column(length = 320)
  private String metaDescription;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 16)
  private PostStatus status = PostStatus.DRAFT;

  private Instant publishedAt;

  @CreatedDate
  @Column(nullable = false, updatable = false)
  private Instant createdAt;

  @LastModifiedDate
  @Column(nullable = false)
  private Instant updatedAt;

  PostTranslation(Post post, SupportedLocale locale) {
    this.post = post;
    this.locale = locale;
  }

  void updateContent(PostContent content) {
    this.title = content.title();
    this.excerpt = content.excerpt();
    this.body = content.body();
    this.metaTitle = content.metaTitle();
    this.metaDescription = content.metaDescription();
  }

  void markPublished(Instant now) {
    this.status = PostStatus.PUBLISHED;
    if (this.publishedAt == null) {
      this.publishedAt = now;
    }
  }

  void markDraft() {
    this.status = PostStatus.DRAFT;
  }
}
