package dev.olekshyr.api.blog;

import com.fasterxml.jackson.annotation.JsonInclude;
import dev.olekshyr.api.shared.PageResponse;
import org.springframework.boot.cache.autoconfigure.RedisCacheManagerBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.serializer.JacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext.SerializationPair;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.json.JsonMapper;

/**
 * The public read caches (Phase 4). Each gets a serializer typed to exactly
 * what it stores: records are final, so a generic serializer writes no type
 * info and would read them back as maps. Registered up front, so Boot binds
 * their hit/miss metrics at startup.
 */
@Configuration(proxyBeanMethods = false)
class BlogCacheConfig {

  static final String POST_LIST = "blog-post-list";
  static final String POST_DETAIL = "blog-post-detail";

  @Bean
  RedisCacheManagerBuilderCustomizer blogCaches(JsonMapper jsonMapper) {
    // Strict reading makes an entry in an old record shape fail, so it's a
    // miss instead of coming back with nulls (D26). Nulls are always written,
    // or a null field would read as missing and every entry would miss.
    JsonMapper mapper = jsonMapper
      .rebuild()
      .changeDefaultPropertyInclusion(inclusion ->
        inclusion
          .withValueInclusion(JsonInclude.Include.ALWAYS)
          .withContentInclusion(JsonInclude.Include.ALWAYS)
      )
      .enable(DeserializationFeature.FAIL_ON_MISSING_CREATOR_PROPERTIES)
      .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
      .build();
    JavaType list = mapper
      .getTypeFactory()
      .constructParametricType(PageResponse.class, PostSummaryResponse.class);
    JavaType detail = mapper.constructType(PostDetailResponse.class);
    return builder ->
      builder
        .withCacheConfiguration(
          POST_LIST,
          typed(builder.cacheDefaults(), mapper, list)
        )
        .withCacheConfiguration(
          POST_DETAIL,
          typed(builder.cacheDefaults(), mapper, detail)
        );
  }

  private static RedisCacheConfiguration typed(
    RedisCacheConfiguration defaults,
    JsonMapper mapper,
    JavaType type
  ) {
    return defaults.serializeValuesWith(
      SerializationPair.fromSerializer(
        new JacksonJsonRedisSerializer<>(mapper, type)
      )
    );
  }
}
