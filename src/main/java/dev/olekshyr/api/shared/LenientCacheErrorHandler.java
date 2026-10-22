package dev.olekshyr.api.shared;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.data.redis.serializer.SerializationException;

/**
 * The cache never fails a request (D25). A failed get is a miss: the method
 * runs and its result overwrites the entry. An entry in an old shape (D26) is
 * expected after a deploy that changed a cached record, so it's only logged at
 * debug; anything else, like Redis being unreachable, at warn. Warnings carry
 * the exception's message only: while Redis is down every request fails twice
 * (get, put), and a stack trace each time would flood the logs. The full trace
 * is logged at debug.
 */
@Slf4j
class LenientCacheErrorHandler implements CacheErrorHandler {

  @Override
  public void handleCacheGetError(
    RuntimeException exception,
    Cache cache,
    Object key
  ) {
    if (exception instanceof SerializationException) {
      log.debug(
        "Cache entry {}::{} has an outdated shape; reading from the database",
        cache.getName(),
        key,
        exception
      );
    } else {
      warn(
        "Cache get failed for " +
        cache.getName() +
        "::" +
        key +
        "; reading from the database",
        exception
      );
    }
  }

  @Override
  public void handleCachePutError(
    RuntimeException exception,
    Cache cache,
    Object key,
    Object value
  ) {
    warn("Cache put failed for " + cache.getName() + "::" + key, exception);
  }

  @Override
  public void handleCacheEvictError(
    RuntimeException exception,
    Cache cache,
    Object key
  ) {
    warn("Cache evict failed for " + cache.getName() + "::" + key, exception);
  }

  @Override
  public void handleCacheClearError(RuntimeException exception, Cache cache) {
    warn(
      "Cache clear failed for " +
      cache.getName() +
      "; entries may be stale until the TTL",
      exception
    );
  }

  private static void warn(String message, RuntimeException exception) {
    log.warn("{}: {}", message, exception.toString());
    log.debug(message, exception);
  }
}
