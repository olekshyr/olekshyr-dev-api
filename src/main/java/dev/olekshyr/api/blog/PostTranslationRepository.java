package dev.olekshyr.api.blog;

import dev.olekshyr.api.shared.SupportedLocale;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface PostTranslationRepository
  extends JpaRepository<PostTranslation, Long>, VisiblePostQueries {
  /** The published translations of these posts, with the post fetched. */
  @Query(
    """
    SELECT t FROM PostTranslation t JOIN FETCH t.post p
    WHERE p.id IN :postIds
      AND t.status = dev.olekshyr.api.blog.PostStatus.PUBLISHED
    """
  )
  List<PostTranslation> findPublishedByPostIdIn(
    @Param("postIds") Collection<Long> postIds
  );

  @Query(
    """
    SELECT t FROM PostTranslation t JOIN FETCH t.post p
    WHERE p.slug = :slug
      AND t.locale = :locale
      AND t.status = dev.olekshyr.api.blog.PostStatus.PUBLISHED
      AND p.deletedAt IS NULL
    """
  )
  Optional<PostTranslation> findVisibleBySlug(
    @Param("slug") String slug,
    @Param("locale") SupportedLocale locale
  );

  @Query(
    """
    SELECT t.locale FROM PostTranslation t
    WHERE t.post.slug = :slug
      AND t.status = dev.olekshyr.api.blog.PostStatus.PUBLISHED
      AND t.post.deletedAt IS NULL
    """
  )
  List<SupportedLocale> findVisibleLocalesBySlug(@Param("slug") String slug);

  /**
   * Visible in the locale and before the given position in (publishedAt,
   * slug) order, nearest first: the list's order (D27), so a post's previous
   * neighbour is the one below it on the newest-first list.
   */
  @Query(
    """
    SELECT t FROM PostTranslation t JOIN FETCH t.post p
    WHERE t.locale = :locale
      AND t.status = dev.olekshyr.api.blog.PostStatus.PUBLISHED
      AND p.deletedAt IS NULL
      AND (t.publishedAt < :publishedAt
        OR (t.publishedAt = :publishedAt AND p.slug < :slug))
    ORDER BY t.publishedAt DESC, p.slug DESC
    """
  )
  List<PostTranslation> findVisibleBefore(
    @Param("locale") SupportedLocale locale,
    @Param("publishedAt") Instant publishedAt,
    @Param("slug") String slug,
    Limit limit
  );

  /** The mirror of {@link #findVisibleBefore}: after, nearest first. */
  @Query(
    """
    SELECT t FROM PostTranslation t JOIN FETCH t.post p
    WHERE t.locale = :locale
      AND t.status = dev.olekshyr.api.blog.PostStatus.PUBLISHED
      AND p.deletedAt IS NULL
      AND (t.publishedAt > :publishedAt
        OR (t.publishedAt = :publishedAt AND p.slug > :slug))
    ORDER BY t.publishedAt ASC, p.slug ASC
    """
  )
  List<PostTranslation> findVisibleAfter(
    @Param("locale") SupportedLocale locale,
    @Param("publishedAt") Instant publishedAt,
    @Param("slug") String slug,
    Limit limit
  );
}
