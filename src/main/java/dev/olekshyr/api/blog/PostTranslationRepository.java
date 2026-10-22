package dev.olekshyr.api.blog;

import dev.olekshyr.api.shared.SupportedLocale;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface PostTranslationRepository
  extends JpaRepository<PostTranslation, Long> {
  @Query(
    value = """
    SELECT t FROM PostTranslation t JOIN FETCH t.post p
    WHERE t.locale = :locale
      AND t.status = dev.olekshyr.api.blog.PostStatus.PUBLISHED
      AND p.deletedAt IS NULL
    ORDER BY t.publishedAt DESC, t.id DESC
    """,
    countQuery = """
    SELECT COUNT(t) FROM PostTranslation t
    WHERE t.locale = :locale
      AND t.status = dev.olekshyr.api.blog.PostStatus.PUBLISHED
      AND t.post.deletedAt IS NULL
    """
  )
  Page<PostTranslation> findVisibleInOrdered(
    @Param("locale") SupportedLocale locale,
    Pageable pageable
  );

  /** Ordering is fixed (publishedAt DESC); any sort in the pageable is dropped. */
  default Page<PostTranslation> findVisibleIn(
    SupportedLocale locale,
    Pageable pageable
  ) {
    return findVisibleInOrdered(
      locale,
      PageRequest.of(pageable.getPageNumber(), pageable.getPageSize())
    );
  }

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
}
