package dev.olekshyr.api.blog;

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

  @Query("SELECT COUNT(p) FROM Post p JOIN p.tags tag WHERE tag.id = :tagId")
  long countPostsUsing(@Param("tagId") Long tagId);
}
