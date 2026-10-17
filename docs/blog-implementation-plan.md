# Blog Feature — Implementation Plan

**Status:** In progress · **Date:** 2026-06-11 · **Decisions agreed:** 2026-10-17

This is a self-paced, step-by-step plan for building the blog feature on top of
the existing foundation (security, error handling, observability, V1 schema).
Each step is scoped to be doable in one sitting and independently testable.
Check items off as you go.

Reference docs: [`project-plan.md`](project-plan.md), [`erd-blog.md`](erd-blog.md),
[`adr/ADR-001-api-key-authentication.md`](adr/ADR-001-api-key-authentication.md),
and [`../GLOSSARY.md`](../GLOSSARY.md) for domain terms (post, translation,
draft, published, visible in a locale, deleted post, …).

New code lives under a new top-level package/module: `dev.olekshyr.api.blog`
(Spring Modulith module — keep internals package-private, expose only what
controllers/other modules need).

---

## Agreed decisions

These are settled. The steps below implement them; if a step and this section
disagree, this section wins.

### Locale

- **D1 — Locale comes from `Accept-Language`, not the URL or a query param.**
  Configure `AcceptHeaderLocaleResolver` with supported locales `en`, `uk`, `ja`
  (language-only, so `uk-UA` matches `uk`) and default `en`. A missing or
  unsupported header falls back to English, with no 400. The same header picks
  the content language and the error-message language.
- **D2 — The frontend sends the locale from its URL, never the visitor's browser header.**
  The Next.js server takes the locale from the route segment (`/uk/blog/…`) and
  sets it as `Accept-Language` on its API call. The URL is the source of truth
  for search engines. Forwarding the browser header would give an English URL
  Ukrainian content.
- **D3 — Public responses send `Content-Language` and `Vary: Accept-Language`,**
  so Next.js fetch caching and any HTTP cache keep languages apart. Redis cache
  keys (Phase 4) use the *resolved* locale.

### Public read API

- **D4 — Pagination:** offset-based `?page=0&size=20`. `size` defaults to 20
  and is capped at 50. The response uses Spring Data's own shape
  (`spring.data.web.page.serialization-mode=via_dto`):
  `{ content, page: { number, size, totalElements, totalPages } }`. It applies
  to every list endpoint, public and admin.
- **D5 — Missing translation:** if the slug exists but the post isn't visible
  in the requested locale, return `404 TRANSLATION_NOT_AVAILABLE` with the
  locales it *is* published in listed in `details`. Every successful post
  response also includes `availableLocales`, which the frontend uses for
  hreflang. An unknown or deleted slug returns a plain `404 NOT_FOUND`.
- **D6 — Tag names:** if a tag has no name in the requested locale, return its
  `slug` as the name; never drop the tag. `GET /api/v1/tags` lists only tags
  used by at least one post visible in that locale.
- **D7 — Post detail fields** include `publishedAt`, the translation's
  `updatedAt` (the frontend's JSON-LD `dateModified`), `readTimeMinutes`,
  `tags` and `availableLocales`. `metaTitle` falls back to `title` when empty.

### Lifecycle

- **D8 — Unpublish** sets a translation back to `DRAFT` and **keeps**
  `published_at`. Re-publishing restores the original date. There are no extra
  states.
- **D9 — Publishing requires** `excerpt` and `metaDescription`, beyond what the
  DB enforces (`title`, `body`). If either is missing, return
  `400 VALIDATION_FAILED` with per-field `details`. Saving a draft needs only
  the DB-required fields.
- **D10 — Slug:** the admin UI suggests it from the English title. The API only
  validates the format (`^[a-z0-9]+(-[a-z0-9]+)*$`) and uniqueness
  (`409 DUPLICATE_SLUG`). It can change freely until the post is first
  published in any locale; after that a change returns `409 SLUG_FROZEN`. There
  are no redirects in v1. A deleted post's slug stays reserved forever (the DB
  unique constraint already covers deleted rows).
- **D11 — Edits to a published translation go live on save.** There's no
  pending-changes copy in v1; to stage a big rewrite, unpublish first.
- **D12 — Not in v1:** deleting a single translation (unpublish instead),
  restoring a deleted post (by hand in the DB if ever needed), and unpublishing
  a whole post across all locales (soft delete covers that).

### Admin API

- **D13 — Admin URLs use the numeric post id;** public URLs use the slug. The id
  is returned by admin endpoints only.
- **D14 — Fine-grained writes,** one call per admin UI action (each locale tab
  saves on its own). `PUT` replaces the whole resource, with no partial
  updates. See 3.4 for the endpoint list.
- **D15 — Admin reads ignore `Accept-Language` for content.** They always
  return every translation, and every tag name with its locale.
  `Accept-Language` only picks the language of error messages.
- **D16 — Admin list:** paginated (D4), sorted by `updatedAt` descending,
  deleted posts hidden unless `?deleted=true`. Each row has id, slug, each
  translation's status and `updatedAt`.

### Errors

- **D17 — The backend localizes error messages** through `MessageSource` from
  `Accept-Language`, so the frontend displays `message` as-is. Add
  `messages_ja.properties`, and every new `ErrorCode` gets keys in all three
  files. `code` and the field-level `code` are a **stable contract**: never
  rename them. The frontend may override text for a specific code if it wants
  nicer copy.
- New codes in this plan: `TRANSLATION_NOT_AVAILABLE` (404),
  `DUPLICATE_SLUG` (409), `SLUG_FROZEN` (409), `TAG_IN_USE` (409),
  `CONFLICT` (409, generic fallback).

### Frontend responsibilities (no API work, recorded for the Next.js side)

- Send the URL's locale as `Accept-Language` (D2).
- `<link rel="alternate" hreflang="...">` from `availableLocales`;
  schema.org `Article` JSON-LD (`headline`, `description`, `datePublished`,
  `dateModified`, `author`, `inLanguage`, `url`).
- Render "not available in this language" on `TRANSLATION_NOT_AVAILABLE`, with
  no fallback.
- Admin post editor with locale tabs (EN | UK | JA) and a slug field suggested
  from the English title; a `/admin/tags` page.

**Out of scope for v1:** filter by tag (data model is ready; endpoint in v2),
site search (v2), comments, scheduled publishing, media library, cover image
(needs the media upload pipeline).

---

## Phase 1 — Domain model (entities & repositories)

Goal: Java types that map cleanly onto the existing `V1__create_posts.sql`
schema. No endpoints yet — verify with `@DataJpaTest` against TestContainers.

> Status 2026-10-17: 1.1–1.6 done (entities with lifecycle rules, repositories,
> `PostLifecycleTest` unit tests passing). 1.7 is done (`BlogRepositoryTest`,
> `@DataJpaTest` + Postgres Testcontainer): the repository tests ran and passed
> (12/12; full suite 37/37 green on 2026-10-17).
>
> Deviations: no separate `TagTranslationRepository` (translations are reached
> through `Tag`, cascade ALL); `@EnableJpaAuditing` lives in `blog/JpaAuditingConfig`
> (not on `ApiApplication`, which broke `@WebMvcTest` slices); Hibernate-enhancer
> synthetic methods are excluded from SpotBugs EI_EXPOSE_REP in
> `spotbugs-exclude.xml`; with `enableAssociationManagement` the enhancer
> auto-adds children to parent collections, so `putTranslation`/`putName` add
> only if absent.

- [x] **1.1 Shared enums**
  Create `dev.olekshyr.api.blog.SupportedLocale` (`EN, UK, JA`) and
  `dev.olekshyr.api.blog.PostStatus` (`DRAFT, PUBLISHED`).
  ⚠️ `PostStatus` can use `@Enumerated(EnumType.STRING)` (CHECK values are
  uppercase), but `Locale` **cannot** — the CHECK constraint is lowercase
  (`'en','uk','ja'`) and `EnumType.STRING` would write `EN` and violate it.
  Use an `AttributeConverter<Locale, String>` (`autoApply = true`) that
  lower-cases on write and upper-cases on read. The `{locale}` path segment in
  admin URLs needs a separate case-insensitive Spring `Converter<String,
  SupportedLocale>` (Phase 3).

- [x] **1.2 `Tag` + `TagTranslation` entities**
  Start here — simplest pair, no publishing logic. `Tag` has `id`, `slug`,
  `color`, timestamps. `TagTranslation` has `id`, `tagId`/`@ManyToOne tag`,
  `locale`, `name`. Map the two unique constraints
  (`(tag_id, locale)` and `(locale, name)`) with `@UniqueConstraint` in
  `@Table` for documentation parity even though Flyway already enforces them.

- [x] **1.3 `Post` + `PostTranslation` entities**
  `Post`: `id`, `slug`, `createdAt`, `updatedAt`, `deletedAt`. `PostTranslation`:
  `id`, `@ManyToOne post`, `locale`, `title`, `excerpt`, `body`, `metaTitle`,
  `metaDescription`, `status`, `publishedAt`, timestamps. Use
  `@OneToMany(mappedBy = "post", cascade = CascadeType.ALL, orphanRemoval = true)`
  on `Post.translations`. Put the lifecycle rules on the entities, where
  they're easy to unit test: `publish()` (D8/D9), `unpublish()` (D8),
  `changeSlug()` (D10, frozen once any translation has `publishedAt`).

- [x] **1.4 Post ↔ Tag many-to-many**
  `post_tags` has no columns beyond the composite PK, so a plain
  `@ManyToMany` with `@JoinTable("post_tags", joinColumns = post_id,
  inverseJoinColumns = tag_id)` on `Post` is sufficient — no separate entity
  needed.

- [x] **1.5 Auditing timestamps**
  JPA auditing (`@EnableJpaAuditing` + `@CreatedDate`/`@LastModifiedDate`).
  Only `posts`, `post_translations`, and `tags` have timestamp columns —
  `tag_translations` has none, so don't add auditing fields there.

- [x] **1.6 Repositories**
  - `PostRepository extends JpaRepository<Post, Long>` — add
    `findBySlugAndDeletedAtIsNull(String slug)`, `existsBySlug(String slug)`
    (includes deleted rows, per D10), and the admin list query (D16).
  - `PostTranslationRepository` — add a paginated query for the public list:
    published-only, filtered by `locale`, **and `post.deletedAt IS NULL`**
    (the index `(locale, status, published_at DESC)` doesn't cover soft
    deletes — without this filter, soft-deleted posts still appear in the
    list), ordered by `published_at DESC`.
    ⚠️ Do **not** `JOIN FETCH` the `tags` collection together with `Pageable`
    — Hibernate falls back to in-memory pagination (HHH90003004) and loads
    the entire result set. Instead: `JOIN FETCH p.post` only (to-one is
    safe), then load tags for the page's posts in a second query or with
    `@BatchSize` on `Post.tags`.
  - `TagRepository` + `TagTranslationRepository` similarly, including the
    "tags used by a post visible in locale X" query for D6.

- [x] **1.7 Verify with `@DataJpaTest`**
  Write one test per repository that persists a row and reads it back via the
  custom query. This is your checkpoint before moving to Phase 2 — if the
  mappings are wrong, fix them here while the surface area is small.

---

## Phase 2 — Public read API

Goal: `GET /api/v1/posts`, `GET /api/v1/posts/{slug}` and `GET /api/v1/tags`
return real data. Routes fall under the existing public filter chain
(`PUBLIC_API_KEY`). The locale always comes from `Accept-Language` (D1).

- [ ] **2.0 Locale and error plumbing**
  - `LocaleResolver` bean: `AcceptHeaderLocaleResolver` with supported
    `en`/`uk`/`ja`, default `en` (D1). Map the resolved `java.util.Locale` to
    `SupportedLocale` in one place.
  - Add `Content-Language` and `Vary: Accept-Language` to public responses (D3),
    e.g. a small `OncePerRequestFilter` or `ResponseBodyAdvice`.
  - Add `messages_ja.properties` with the existing keys (D17).
  - Set `spring.data.web.page.serialization-mode=via_dto` (D4).

- [ ] **2.1 Response DTOs**
  `PostSummaryResponse` (slug, title, excerpt, tags, publishedAt,
  readTimeMinutes) for the list. `PostDetailResponse` adds body, metaTitle (D7
  fallback), metaDescription, updatedAt and availableLocales. `TagResponse`
  has slug, name (D6 fallback) and color. Use Java `record`s, consistent with
  `ApiErrorResponse`.

- [ ] **2.2 Read-time calculator**
  Small utility (`ReadTimeCalculator` or similar) — word count of `body` / 200,
  rounded up, minimum 1. The body is HTML — strip tags before counting
  (a simple `replaceAll("<[^>]*>", " ")` is fine at this fidelity), otherwise
  markup inflates the count. Per the ERD this is never persisted. Unit test
  with known word counts, including one HTML-heavy input. Note: word counting
  by whitespace undercounts Japanese, which doesn't put spaces between words.
  Count characters for `ja` (≈500 chars/min is a common estimate).

- [ ] **2.3 `PostQueryService`**
  `listPublished(SupportedLocale, Pageable)` and
  `getPublished(String slug, SupportedLocale)`. The latter throws a domain
  `TranslationNotAvailableException` carrying the available locales (D5) when
  the post exists but isn't visible in that locale, and a not-found exception
  when the slug is unknown or deleted. Map both in `GlobalExceptionHandler`.

- [ ] **2.4 `PostController`**
  - `GET /api/v1/posts?page=0&size=20` → page of `PostSummaryResponse` (D4: cap `size` at 50)
  - `GET /api/v1/posts/{slug}` → `PostDetailResponse`

  Gotcha: an unparseable path/query value (e.g. a non-numeric `{id}` in Phase 3
  or a bad `{locale}` segment) throws `MethodArgumentTypeMismatchException`,
  which `GlobalExceptionHandler` does **not** catch today — it would fall
  through as a 500 `INTERNAL_ERROR`. Add an
  `@ExceptionHandler(MethodArgumentTypeMismatchException.class)` mapping to
  400 `VALIDATION_FAILED`.

- [ ] **2.5 `GET /api/v1/tags`**
  Tags used by at least one post visible in the resolved locale, with
  translated names (D6). Same pattern as posts.

- [ ] **2.6 Manual smoke test**
  `mvnw.cmd spring-boot:run`, hit the endpoints with `curl` + your
  `PUBLIC_API_KEY`, once per `Accept-Language` (`en`, `uk`, `ja`, `fr`, none).
  Check Swagger UI at `/swagger-ui.html` renders them correctly. Insert a
  couple of rows manually via `psql` first if needed.

---

## Phase 3 — Admin write API

Goal: create/edit/publish posts and manage tags via `/api/v1/admin/**`
(admin filter chain, `ADMIN_API_KEY`). Posts are addressed by numeric id (D13).

- [ ] **3.1 HTML sanitization**
  Add OWASP Java HTML Sanitizer dependency. Create a `BodySanitizer`
  component with a policy allowing the basic blog-content tags (p, headings,
  lists, links, images, code/pre, blockquote, etc.), compatible with what
  CKEditor emits. Unit test with a `<script>` injection payload to confirm
  it's stripped.

- [ ] **3.2 Admin request DTOs + validation**
  `CreatePostRequest` (slug), `UpdatePostRequest` (slug),
  `SetPostTagsRequest` (tag slugs), `UpsertTranslationRequest` (title,
  excerpt, body, metaTitle, metaDescription), `UpsertTagRequest` (slug, color,
  names per locale). Use Bean Validation (`@NotBlank`, `@Size` matching the
  column lengths, `@Pattern` for slug format and `#RRGGBB` color); these flow
  through the existing `VALIDATION_FAILED` path.

- [ ] **3.3 `PostAdminService`**
  - `create(slug)` → creates a `Post` with no translations. Pre-check
    `existsBySlug` → `409 DUPLICATE_SLUG` (D10).
  - `changeSlug(id, slug)` → `409 SLUG_FROZEN` once published in any locale,
    `409 DUPLICATE_SLUG` if taken (D10).
  - `upsertTranslation(id, locale, dto)` → create-or-replace, running `body`
    through `BodySanitizer`. Live immediately if published (D11).
  - `publish(id, locale)` → D9 checks, `status = PUBLISHED`, `published_at`
    set only if null (D8).
  - `unpublish(id, locale)` → `status = DRAFT`, `published_at` kept (D8).
  - `softDelete(id)` → sets `posts.deleted_at = now()`.
  - `setTags(id, tagSlugs)` → replaces the tag set; unknown tag slug → 400.
  - `list(pageable, includeDeleted)` and `get(id)` (draft preview: every
    translation, any status) — D15/D16.

- [ ] **3.4 `PostAdminController`** — under `/api/v1/admin/posts`:
  | Method & path | Action | Response |
  |---|---|---|
  | `GET /?page&size&deleted` | admin list (D16) | 200 page |
  | `GET /{id}` | draft preview, all translations | 200 |
  | `POST /` | create | 201 + `Location` |
  | `PUT /{id}` | change slug | 200 |
  | `DELETE /{id}` | soft delete | 204 |
  | `PUT /{id}/tags` | replace tags | 200 |
  | `PUT /{id}/translations/{locale}` | create/replace translation | 200 |
  | `POST /{id}/translations/{locale}/publish` | publish | 200 |
  | `POST /{id}/translations/{locale}/unpublish` | unpublish | 200 |

- [ ] **3.5 `TagAdminController`** — under `/api/v1/admin/tags`:
  `GET /` (all tags, all names), `POST /` (201), `PUT /{id}` (replace slug,
  color and names), `DELETE /{id}` (204).
  ⚠️ Don't blanket-map `DataIntegrityViolationException` → `TAG_IN_USE` in
  `GlobalExceptionHandler` — every unique-constraint violation (duplicate
  slug, duplicate tag name) throws the same exception, and they'd all get
  mislabeled. Instead, pre-check in the service (count `post_tags` rows for
  the tag) and throw a domain `TagInUseException` mapped to 409 `TAG_IN_USE`.
  Keep the FK `ON DELETE RESTRICT` as the race-condition backstop, and add a
  generic `DataIntegrityViolationException` → 409 `CONFLICT` handler as the
  fallback.

- [ ] **3.6 Manual smoke test**
  Full lifecycle via `curl` with `ADMIN_API_KEY`: create post → add `en`
  translation → publish → confirm it appears in the public list → try to
  change the slug (expect 409) → unpublish → confirm it disappears → publish
  again (same `publishedAt`) → soft-delete → confirm it's gone and the slug
  can't be reused.

---

## Phase 4 — Caching

Goal: cache the public read paths; invalidate on admin writes. Redis is
already a dependency and running via `compose.yaml`.

- [ ] **4.1 Enable Spring Cache**
  `@EnableCaching` + Redis-backed `CacheManager` bean (or rely on Spring Boot
  autoconfiguration with `spring.cache.type=redis`). Set a sensible default
  TTL (e.g. 5 minutes) — this is a low-traffic personal blog, so correctness
  after a publish matters more than hit rate.

- [ ] **4.2 `@Cacheable` on read methods**
  Cache `listPublished` and `getPublished` from `PostQueryService`, keyed by
  `(resolvedLocale, page, size)` / `(slug, resolvedLocale)` (D3).
  ⚠️ Cache the DTOs, never `Page`/`PageImpl` or entities — `PageImpl` has no
  default constructor and fails Jackson deserialization on Redis read, and
  entities drag lazy proxies into the serializer. Map to a serializable page
  DTO (the same shape `via_dto` produces) before caching. Configure
  `GenericJackson2JsonRedisSerializer` (or register the records with the
  default typing) and verify a cache **read** path in a test, not just the
  write.

- [ ] **4.3 `@CacheEvict` on writes**
  Evict on every admin write that can change public output: publish,
  unpublish, upsertTranslation (edits go live, D11), setTags, softDelete, and
  tag changes. Simplest correct option: `allEntries = true` on the relevant
  cache names — fine at this scale.

- [ ] **4.4 Verify with logs/Grafana**
  Confirm cache hits/misses show up (datasource-micrometer is already wired)
  and that publishing a post is reflected immediately.

---

## Phase 5 — Tests

Goal: lock in behavior with TestContainers-backed integration tests
(`TestcontainersConfiguration` / `TestApiApplication` already exist).

- [ ] **5.1 Repository tests** (if not already done in 1.7, expand coverage:
  pagination, locale filtering, soft-delete filtering, the unique constraints
  failing as expected).

- [ ] **5.2 Public API integration tests**
  `MockMvc` + real Postgres/Redis containers: list posts (empty, populated,
  paginated, `size` cap), locale resolution (`uk`, `uk-UA`, `fr` → en, no
  header → en), `Content-Language`/`Vary` headers, get single post (found,
  unknown slug → `NOT_FOUND`, not in locale → `TRANSLATION_NOT_AVAILABLE` with
  available locales, deleted → `NOT_FOUND`), tag name fallback,
  missing/invalid `PUBLIC_API_KEY` → 401.

- [ ] **5.3 Admin API integration tests**
  Full create → translate → publish → unpublish → re-publish (same
  `publishedAt`) → tag → soft-delete lifecycle. Publish without excerpt →
  `VALIDATION_FAILED` with field details. Slug frozen after publish, slug of a
  deleted post not reusable, tag in use can't be deleted. Error messages
  localized for `uk` and `ja`. Wrong/missing `ADMIN_API_KEY` → 403.
  Cross-tier check: a valid `PUBLIC_API_KEY` against an admin route → 403.

- [ ] **5.4 Sanitizer + read-time + entity lifecycle unit tests**
  Pure unit tests, no containers needed — fast feedback loop.

- [ ] **5.5 Modulith boundary test**
  `ApplicationModules.of(ApiApplication.class).verify()` —
  `spring-modulith-starter-test` is already on the classpath. This is the
  first real second module, so this test starts paying for itself now
  (catches accidental references into `blog` internals from other packages).

---

## Phase 6 — Polish

- [ ] **6.1 OpenAPI grouping + auth** — split public vs admin paths into
  separate springdoc groups so `/swagger-ui.html` is navigable, and register
  an `apiKey` security scheme (header `X-API-Key`) so the Authorize button
  works — without it every "Try it out" call 401s. Document `Accept-Language`
  on public endpoints and list every error `code` (D17: they're the contract).
- [ ] **6.2 Error message keys** — every new `ErrorCode` has keys in
  `messages.properties`, `messages_uk.properties` and `messages_ja.properties`.
  Consider a unit test that fails if any `ErrorCode` key is missing from any
  of the three files.
- [ ] **6.3 ADR** — if the caching strategy or sanitization policy involved a
  non-obvious tradeoff, write an ADR following the existing
  `docs/adr/ADR-001` format.
- [ ] **6.4 Connect the frontend** — point the Personal Website project at
  the new public endpoints (see "Frontend responsibilities" above).

---

## Suggested order of attack

Phases 1 → 2 → 3 → 4 → 5 → 6, but within Phase 1, do tags before posts
(simpler shape, same patterns). After Phase 2 you'll have a working,
demoable read API even before admin/auth-for-writes exists — a good morale
checkpoint. The frontend can start against the Phase 2 OpenAPI spec while
Phase 3 is in progress.
