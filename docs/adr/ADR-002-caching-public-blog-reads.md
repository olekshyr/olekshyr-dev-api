# ADR-002: Caching Public Blog Reads in Redis

**Status:** Accepted
**Date:** 2026-11-02 (implemented 2026-10-22, blog plan Phase 4, decisions D24–D26)

## Context

The public blog API has two reads: the paginated list of posts visible in a locale, and a single post by slug. Every page view of the website hits one of them. Both build their response from several queries, and the list also loads every body on the page to compute read time, which is never stored.

The data changes rarely and only through the admin API: one author, a handful of writes a day. Nothing changes public output on its own; there is no scheduled publishing in v1. Redis already runs next to the application.

Two constraints shape the design:

- A stale public page is acceptable for moments, not for long. After the author publishes, unpublishes or edits a post, the website should show it on the next request.
- The cache is an optimization, never a dependency. A Redis outage must not take the blog down.

## Decision

Cache the two public reads with Spring Cache over Redis, cache-aside, and keep it correct by eviction rather than expiry.

- **What is cached:** the response records (`PageResponse<PostSummaryResponse>` and `PostDetailResponse`), never entities or Spring Data pages. Two caches, `blog-post-list` (key `locale:page:size`) and `blog-post-detail` (key `slug:locale`), keyed by the *resolved* locale. Errors (404s) are never cached, and neither are empty pages past the first.
- **Eviction:** every admin write, posts and tags alike, clears both caches entirely. One rule for every write method is easier to keep correct than deciding which writes can affect which entries. Writes are rare, so the cost of an over-eager clear is a few extra database reads.
- **After the commit:** the caching advice wraps the transaction advice (`@EnableCaching(order = HIGHEST_PRECEDENCE)`), so the clear runs once the write has committed. Clearing before the commit would let a concurrent read cache the old data again. Spring's `transactionAware()` cache decorator was rejected: its deferred evictions run outside the cache error handler, so a failed one would turn an already-committed write into a 500. Cache writes are immediate (Spring Data Redis 4 writes asynchronously by default), so the clear has finished before the admin response goes out.
- **TTL as a safety net:** entries expire after one hour. The TTL doesn't keep the cache correct; it only bounds how long a missed eviction (Redis down during a write, a manual database edit, a bug) can show stale content.
- **The cache never fails a request:** a cache error handler logs and swallows every failure. A failed read is a miss and the request reads from Postgres; a failed write or clear is logged at `warn`. Redis timeouts are 1 second, so an outage costs each request at most two timeouts.
- **A changed shape is a miss:** each cache has its own Jackson serializer typed to its record, built from the application's mapper but strict (missing or unknown properties fail). A deploy doesn't clear Redis; after one that changes a cached record, old entries fail to read, count as misses and are overwritten. No cache migration or version prefix is needed.
- **Clears use SCAN, never KEYS,** which blocks Redis and is often disabled on hosted plans.

## Consequences

**Benefits:**

- Public reads skip Postgres entirely on a hit, including the body loading for read time.
- An admin write is visible on the next public request, with no waiting for a TTL.
- A Redis outage degrades to uncached reads, never to errors.
- Adding, removing or renaming a field on a cached record needs no cache handling.
- Hit and miss counts per cache are exported as metrics (`cache.gets`).

**Tradeoffs:**

- **An accepted race:** a public read that loads from Postgres just before a write commits can store its old result after that write's clear. It stays until the TTL or the next admin write. The window is one read's database time; on a low-traffic, single-author blog this is rare enough not to justify a delayed second clear. If it happens, saving again or deleting the `blog-post-*` keys fixes it.
- An eviction that fails while Redis is unreachable leaves stale entries for up to an hour.
- A change made straight in the database (psql, a seed script) shows only after the TTL, unless the `blog-post-*` keys are deleted by hand.
- Every write clears every public entry, including pages it didn't touch. This stops scaling if writes become frequent.
- Tests need a real Redis, and every cache must be cleared between tests, since the database seed doesn't touch Redis.

## Alternatives Considered

**HTTP caching only (`Cache-Control`, ETags, a CDN):**
The responses already send `Vary: Accept-Language`, so an HTTP cache could work. But invalidation on publish would need CDN purges or short max-ages, and the Next.js server, the only client, would still need its own caching. Server-side caching keeps invalidation next to the writes. HTTP caching can still be layered on later.

**TTL-only expiry, no eviction:**
Simpler, but a publish would show only after the TTL, and a short TTL loses most of the benefit. Rejected for the "next request" requirement.

**Targeted eviction (only the affected slug and pages):**
Fewer misses after a write, but a single change (a publish, a tag rename) can move a post across every list page in every locale. Working out the affected keys per write is error-prone, and writes are too rare for the savings to matter.

**An in-process cache (Caffeine):**
No network hop and no outage mode, but each application instance would hold its own copy, and an admin write handled by one instance couldn't evict the others. Redis is already part of the stack.

**`transactionAware()` cache decoration:**
Defers evictions to after the commit, which is the right timing, but runs them outside the cache error handler. Rejected in favor of ordering the caching advice outside the transaction (see above).
