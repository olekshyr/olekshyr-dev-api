package dev.olekshyr.api.blog;

import dev.olekshyr.api.shared.SupportedLocale;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface TagRepository extends JpaRepository<Tag, Long> {
  Optional<Tag> findBySlug(String slug);

  boolean existsBySlug(String slug);

  List<Tag> findBySlugIn(Collection<String> slugs);

  @Query(
    """
    SELECT DISTINCT tag FROM Post p JOIN p.tags tag JOIN p.translations t
    WHERE t.locale = :locale
      AND t.status = dev.olekshyr.api.blog.PostStatus.PUBLISHED
      AND p.deletedAt IS NULL
    ORDER BY tag.slug
    """
  )
  List<Tag> findUsedByPostsVisibleIn(@Param("locale") SupportedLocale locale);

  @Query("SELECT COUNT(p) FROM Post p JOIN p.tags tag WHERE tag.id = :tagId")
  long countPostsUsing(@Param("tagId") Long tagId);
}
