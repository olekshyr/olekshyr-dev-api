package dev.olekshyr.api.blog;

import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

interface PostRepository extends JpaRepository<Post, Long> {
  Optional<Post> findBySlugAndDeletedAtIsNull(String slug);

  /** Includes soft-deleted posts: their slugs stay reserved. */
  boolean existsBySlug(String slug);

  Page<Post> findByDeletedAtIsNull(Pageable pageable);
}
