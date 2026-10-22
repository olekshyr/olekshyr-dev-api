package dev.olekshyr.api.shared;

import org.springframework.boot.cache.autoconfigure.RedisCacheManagerBuilderCustomizer;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.data.redis.cache.BatchStrategies;
import org.springframework.data.redis.cache.RedisCacheWriter;
import org.springframework.data.redis.connection.RedisConnectionFactory;

/**
 * Spring Cache over Redis; Boot builds the cache manager, modules register
 * their own caches. The caching advice runs outside the transaction advice,
 * so an eviction happens after the write has committed and a failed one goes
 * through the error handler like any other cache call (D24, D25).
 */
@Configuration(proxyBeanMethods = false)
@EnableCaching(order = Ordered.HIGHEST_PRECEDENCE)
public class CacheConfig implements CachingConfigurer {

  @Override
  public CacheErrorHandler errorHandler() {
    return new LenientCacheErrorHandler();
  }

  /**
   * Spring Data Redis 4 writes asynchronously by default: an eviction could
   * still be running when the admin response goes out, and a failed put or
   * clear would never reach the error handler. Immediate writes finish before
   * the call returns.
   */
  @Bean
  RedisCacheManagerBuilderCustomizer immediateCacheWrites(
    RedisConnectionFactory connectionFactory
  ) {
    return builder ->
      builder.cacheWriter(
        RedisCacheWriter.create(
          connectionFactory,
          // SCAN, not the default KEYS: KEYS blocks Redis and hosted plans
          // often disable it, which would make every eviction fail
          writer ->
            writer.immediateWrites().batchStrategy(BatchStrategies.scan(1000))
        )
      );
  }
}
