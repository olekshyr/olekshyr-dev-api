package dev.olekshyr.api.blog;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.cache.annotation.CacheEvict;

/**
 * Clears both public read caches after a successful admin write (D24). Goes on
 * every write method, even ones that can't change public output today: one
 * rule is easier to keep correct. Never on the class, or admin reads would
 * clear the cache too.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@CacheEvict(
  cacheNames = { BlogCacheConfig.POST_LIST, BlogCacheConfig.POST_DETAIL },
  allEntries = true
)
@interface EvictsPublicPosts {
}
