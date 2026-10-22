package dev.olekshyr.api;

import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.test.context.TestContext;
import org.springframework.test.context.TestExecutionListener;

/**
 * Clears every cache before each test. Redis outlives the test, and
 * blog-seed.sql truncates the tables but not the cache, so cached responses
 * would leak from one test into the next.
 */
public class ClearCachesTestExecutionListener implements TestExecutionListener {

  @Override
  public void beforeTestMethod(TestContext testContext) {
    testContext
      .getApplicationContext()
      .getBeanProvider(CacheManager.class)
      .ifAvailable(ClearCachesTestExecutionListener::clearAll);
  }

  private static void clearAll(CacheManager caches) {
    for (String name : caches.getCacheNames()) {
      Cache cache = caches.getCache(name);
      if (cache != null) {
        cache.clear();
      }
    }
  }
}
