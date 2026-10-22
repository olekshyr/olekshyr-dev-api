# Blog Feature — Implementation Plan

**Status:** In progress · **Date:** 2026-06-11 · **Decisions agreed:** 2026-10-17
(Phase 2 review: 2026-10-22; Phase 3–6 checked against the code: 2026-10-22;
Phase 4 review: 2026-10-22)

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
- **D3 — Every `/api/v1/**` response sends `Content-Language` and `Vary: Accept-Language`,**
  so Next.js fetch caching and any HTTP cache keep languages apart. This covers
  public and admin routes, success and error responses alike (error messages
  are localized too). The only exception is the empty-body 401/403 from the
  API-key filters, which doesn't vary. Redis cache keys (Phase 4) use the
  *resolved* locale.
- **D18 — Locales in JSON are lowercase BCP 47 strings** (`"en"`, `"uk"`,
  `"ja"`), the same values as `Content-Language`, never the enum names. Lists of
  locales (e.g. `availableLocales`) use the canonical order `en, uk, ja`.

### Public read API

- **D4 — Pagination:** offset-based `?page=0&size=10`. `size` defaults to 10
  and is capped at 50. Out-of-range input is corrected, never rejected: Spring's
  `spring.data.web.pageable.default-page-size=10` / `max-page-size=50` silently
  cap `size=999` to 50, a negative or unparseable value falls back to the
  default, a page past the end returns 200 with empty `content`, and `?sort` is
  ignored. The response is our own `PageResponse<T>` record (in `shared`) with
  Spring Data's shape: `{ content, page: { number, size, totalElements,
  totalPages } }`. It's safe to cache (Phase 4) and explicit in OpenAPI;
  `spring.data.web.page.serialization-mode=via_dto` stays as a safety net. It
  applies to every list endpoint, public and admin.
- **D5 — Missing translation:** if the post is visible in at least one other
  locale but not the requested one, return a plain
  `404 TRANSLATION_NOT_AVAILABLE` (localized message, no locale list). A post
  visible in **no** locale (only drafts), an unknown slug, or a deleted post
  returns a plain `404 NOT_FOUND`, so draft slugs don't leak. Every successful
  post response includes `availableLocales`, which the frontend uses for
  hreflang. (Listing the other locales in the error is a possible
  non-breaking addition later.)
- **D6 — Tag names:** if a tag has no name in the requested locale, return its
  `slug` as the name; never drop the tag. Tags come embedded in post responses
  as `TagResponse` (`slug`, `name`, `color`), sorted by `slug` (stable across
  locales, no collation needed). There is no `GET /api/v1/tags` in v1; it moves
  to v2 with filter-by-tag.
- **D7 — Post detail fields** include `publishedAt`, the translation's
  `updatedAt` (the frontend's JSON-LD `dateModified`), `readTimeMinutes`,
  `tags` and `availableLocales`. `metaTitle` falls back to `title` when empty.
  Known v1 quirk, accepted: auditing bumps `updatedAt` on publish/unpublish too,
  so a re-publish moves `dateModified` without a content change; the frontend
  may hide "updated" when it's close to `publishedAt`. A separate
  `contentUpdatedAt` would need a migration and isn't worth it yet.
- **D19 — Public slug lookup is an exact match.** Any slug that isn't found,
  including a malformed or uppercase one, is a plain `404 NOT_FOUND`: no format
  check (that's a write-side rule, D10), no case folding.

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
  published in any locale; after that a change returns `409 SLUG_FROZEN`.
  Saving the slug the post already has is a no-op, never `DUPLICATE_SLUG` or
  `SLUG_FROZEN`, so the editor can resend the whole form. There
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
  updates. See 3.5 for the endpoint list.
- **D15 — Admin reads ignore `Accept-Language` for content.** They always
  return every translation, and every tag name with its locale.
  `Accept-Language` only picks the language of error messages.
- **D16 — Admin list:** paginated (D4), sorted by `updatedAt` descending,
  deleted posts hidden unless `?deleted=true` (which includes them alongside
  the rest). Each row has id, slug, `deletedAt`, each translation's status and
  `updatedAt`.
- **D23 — Deleted posts on the admin side:** `GET /{id}` still returns a
  deleted post, with `deletedAt` set, so rows from `?deleted=true` open.
  Every write to it returns `404 NOT_FOUND`, because a deleted post can't come
  back (D12). `DELETE` is idempotent: deleting again returns 204 and keeps the
  original `deletedAt`.

### Errors

- **D17 — The backend localizes error messages** through `MessageSource` from
  `Accept-Language`, so the frontend displays `message` as-is. Add
  `messages_ja.properties`, and every new `ErrorCode` gets keys in all three
  files. `code` and the field-level `code` are a **stable contract**: never
  rename them. The frontend may override text for a specific code if it wants
  nicer copy.
- New codes in this plan: `TRANSLATION_NOT_AVAILABLE` (404),
  `UNSUPPORTED_REQUEST` (405/406/415), `DUPLICATE_SLUG` (409),
  `SLUG_FROZEN` (409), `TAG_IN_USE` (409), `CONFLICT` (409, generic fallback),
  `REQUEST_REJECTED` (any other 4xx; added in the Phase 2 review).
- **D20 — Spring MVC's own 4xx exceptions keep their status.**
  `GlobalExceptionHandler` extends `ResponseEntityExceptionHandler` and
  overrides `handleExceptionInternal` to wrap them in the envelope: 400 →
  `VALIDATION_FAILED`, 404 → `NOT_FOUND`, 405/406/415 → `UNSUPPORTED_REQUEST`,
  409 → `CONFLICT`, any other 4xx (including non-standard ones like 499) →
  `REQUEST_REJECTED`, 5xx → `INTERNAL_ERROR`.
  Without this the `Exception` catch-all turns them (e.g. a `POST` to a
  read-only route, or malformed JSON in Phase 3) into `500 INTERNAL_ERROR`.
- **D21 — Error-mapping module boundary:** `shared` defines an abstract
  `ApiException(ErrorCode, HttpStatus)`; every domain exception extends it and
  one handler maps the base class. Dependencies run `blog → shared` only, and
  `ErrorCode` stays the single list of the error contract. `ApiException` can
  carry optional field `details`, so checks the service makes (D9 publish
  requirements, unknown tag slugs) return `400 VALIDATION_FAILED` with the same
  per-field shape as Bean Validation. Field codes: `NOT_BLANK` for a missing
  field (the code `@NotBlank` already produces) and `UNKNOWN_TAG`.

### API docs

- **D22 — Swagger UI and `/v3/api-docs` are open without a key only when
  `app.docs.public=true`** (on locally and in staging, off in prod). They get
  their own filter chain ahead of the public one. Otherwise they sit behind
  `PUBLIC_API_KEY` like everything else.

### Caching

- **D24 — Eviction keeps the cache correct; the TTL is only a safety net.**
  Every admin write clears both public caches once its transaction commits.
  Visibility doesn't depend on time (there's no scheduled publishing in v1), so
  nothing else changes public output. The TTL is 1 hour: it only limits how
  long a missed eviction (Redis down, a manual DB edit, a bug) can show stale
  content. Accepted race: a public read that loads from Postgres just before a
  write commits can store its old result after that write's eviction, and it
  stays until the TTL (or the next admin write). The window is one read's
  database time, and on a low-traffic blog with one author it's rare enough
  not to justify a delayed second eviction; if it bites, save again or clear
  the `blog-post-*` keys.
- **D25 — The cache never fails a request.** If Redis is unreachable, the
  error is logged at `warn` and the request reads from Postgres. An eviction
  that fails while Redis is down can leave stale entries until the TTL (D24).
- **D26 — A cached entry with the wrong shape is a cache miss.** A deploy
  doesn't clear Redis. The cache's Jackson mapper fails on missing and unknown
  properties, so after any change to a cached response record (a field added,
  removed or renamed), old entries fail to read. That counts as a miss, logged
  at `debug`: the request reads from Postgres and the fresh result overwrites
  the entry. A deploy that doesn't change the shape keeps the cache warm.

### Frontend responsibilities (no API work, recorded for the Next.js side)

- Send the URL's locale as `Accept-Language` (D2).
- `<link rel="alternate" hreflang="...">` from `availableLocales`;
  schema.org `Article` JSON-LD (`headline`, `description`, `datePublished`,
  `dateModified`, `author`, `inLanguage`, `url`).
- Render "not available in this language" on `TRANSLATION_NOT_AVAILABLE`, with
  no fallback.
- Admin post editor with locale tabs (EN | UK | JA) and a slug field suggested
  from the English title; a `/admin/tags` page.

**Out of scope for v1:** filter by tag and the public tag list
`GET /api/v1/tags` (data model and the `findUsedByPostsVisibleIn` query are
ready; endpoints in v2),
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
> through `Tag`, cascade ALL); `@EnableJpaAuditing` lives in `JpaAuditingConfig` (now in `shared`)
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
  SupportedLocale>` (Phase 3, step 3.0).

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

Goal: `GET /api/v1/posts` and `GET /api/v1/posts/{slug}` return real data.
Routes fall under the existing public filter chain (`PUBLIC_API_KEY`). The
locale always comes from `Accept-Language` (D1). The public tag list is v2
(D6).

> Status 2026-10-22: 2.0–2.4 and 2.6 done; 2.5 moved to v2. After a code
> review pass: full suite 104/104 green, SpotBugs clean, smoke script 21/21
> with `-Seed`, and Swagger UI checked in a browser with `APP_DOCS_PUBLIC=true`.
>
> Deviations and review fixes:
> - Test overrides moved from `src/test/resources/application.properties` to
>   `src/test/resources/config/application.properties`. The root-level file
>   shadowed the main one, so no main property (pagination, `ddl-auto=validate`,
>   …) applied in tests. Loading it exposed a startup crash, fixed below.
> - The web slice tests use `@WebMvcTest(useDefaultFilters = false)` and
>   `@Import` what they need: a bare `@WebMvcTest` scans `PostController`,
>   whose service isn't in the slice.
> - `GET /api/v1/posts` documents `page`/`size` explicitly and hides the
>   ignored `sort` (part of 6.1).
> - Read time also treats HTML entities (`&nbsp;`) as spaces.
> - The smoke script (`.claude/skills/run-olekshyr-dev-api`) gained a `-Seed`
>   switch, sends the seed to `psql` as UTF-8, and kills the app's process tree
>   on exit.
> - Not changed: the list still loads full bodies to compute read time (the
>   ERD says read time is never stored; Phase 4 caching covers it).
>
> - OTLP startup crash (pre-existing): `management.*.otlp.endpoint=${OTLP_*:}`
>   in `application.properties` resolved to `""` when the env var was unset,
>   and the OTLP log exporter rejects an empty endpoint, so the app only
>   started where Docker Compose supplied the Grafana connection. Now
>   `logging.OtlpEndpointEnvironmentPostProcessor` maps `OTLP_TRACING_ENDPOINT`
>   / `OTLP_LOGGING_ENDPOINT` / `OTLP_METRICS_ENDPOINT` onto Boot's properties
>   only when set. Unset means no trace/log exporter; metrics fall back to
>   Boot's default `localhost:4318` push. Verified by running the packaged jar
>   without Compose or `OTLP_*` vars.

Tests ride along with each step: write the matching 5.2 `MockMvc` cases as you
go, the same way 1.7 did for Phase 1, instead of saving them all for Phase 5.
Test data comes from a checked-in `src/test/resources/blog-seed.sql` (a few
posts across locales, drafts, a deleted post, tags with and without names in
each locale), loaded with `@Sql` in tests and with `psql` for the 2.6 smoke
test. It never goes through Flyway, so it can't reach prod.

- [x] **2.0 Locale and error plumbing**
  - Move `SupportedLocale` (and its JPA converter) from `blog` to `shared`:
    the `LocaleResolver` is global (admin error messages need it too), so it
    can't depend on `blog`.
  - `LocaleConfig` in `shared`: `AcceptHeaderLocaleResolver` with the supported
    list built from `SupportedLocale`, default `en` (D1). Map the resolved
    `java.util.Locale` to `SupportedLocale` in one place.
  - A servlet filter adds `Content-Language: <resolved>` and
    `Vary: Accept-Language` to every `/api/v1/**` response, success or error
    (D3). Serialize locales as lowercase strings in `en, uk, ja` order (D18).
  - `ApiException(ErrorCode, HttpStatus)` base class in `shared`, mapped by one
    handler (D21). Retrofit the existing `SlugFrozenException`,
    `PostDeletedException`, `PublishRequirementsNotMetException` and
    `TranslationNotFoundException` when Phase 3 maps them.
  - `GlobalExceptionHandler` extends `ResponseEntityExceptionHandler` (D20) and
    adds `UNSUPPORTED_REQUEST`. ⚠️ The base class already handles
    `MethodArgumentNotValidException`, `HandlerMethodValidationException` and
    `NoResourceFoundException`; keeping `@ExceptionHandler` methods for them
    fails at startup with an ambiguous-handler error. Move that logic into
    overrides of `handleMethodArgumentNotValid`, `handleHandlerMethodValidation`
    and `handleNoResourceFoundException`. This also covers
    `MethodArgumentTypeMismatchException` (400 `VALIDATION_FAILED`), so no
    separate handler is needed.
  - Add `messages_ja.properties` with the existing keys, plus the new codes in
    all three files (D17).
  - Pagination properties: `spring.data.web.pageable.default-page-size=10`,
    `max-page-size=50`, and `spring.data.web.page.serialization-mode=via_dto`
    as a safety net (D4). `PageResponse<T>` record in `shared`.
  - Docs access: a filter chain ahead of the public one that permits
    `/swagger-ui/**`, `/swagger-ui.html` and `/v3/api-docs/**` when
    `app.docs.public=true` (D22). Set it in `.env` locally.

- [x] **2.1 Response DTOs**
  `PostSummaryResponse` (slug, title, excerpt, tags, publishedAt,
  readTimeMinutes) for the list. `PostDetailResponse` adds body, metaTitle (D7
  fallback), metaDescription, updatedAt and availableLocales (D18 format).
  `TagResponse` has slug, name (D6 fallback) and color; tags are sorted by
  slug. Use Java `record`s, consistent with `ApiErrorResponse`.

- [x] **2.2 Read-time calculator**
  Small utility (`ReadTimeCalculator` or similar) — word count of `body` / 200,
  rounded up, minimum 1. The body is HTML — strip tags before counting
  (a simple `replaceAll("<[^>]*>", " ")` is fine at this fidelity), otherwise
  markup inflates the count. Per the ERD this is never persisted. Unit test
  with known word counts, including one HTML-heavy input. Note: word counting
  by whitespace undercounts Japanese, which doesn't put spaces between words.
  Count characters for `ja` (≈500 chars/min is a common estimate).

- [x] **2.3 `PostQueryService`**
  `listVisible(SupportedLocale, Pageable)` → `PageResponse<PostSummaryResponse>`
  and `getVisible(String slug, SupportedLocale)` (first named `listPublished` /
  `getPublished`; renamed to the glossary's "visible in a locale"). The latter throws:
  - `TranslationNotAvailableException` → `404 TRANSLATION_NOT_AVAILABLE` when
    the post is visible in at least one other locale (D5; no locale list);
  - `PostNotFoundException` → `404 NOT_FOUND` when the slug is unknown,
    deleted, or visible in no locale (D5), matched exactly (D19).

  Both extend `ApiException` (D21). `findVisibleLocalesBySlug` decides the
  branch and also feeds `availableLocales`; it sorts by the DB string
  (`en, ja, uk`), so re-sort into enum order (D18).

- [x] **2.4 `PostController`**
  - `GET /api/v1/posts?page=0&size=10` → `PageResponse<PostSummaryResponse>` (D4)
  - `GET /api/v1/posts/{slug}` → `PostDetailResponse`

  Delete the sample `GreetingController` from `ApiApplication` (and any test
  that hits `/greeting`) now that real public endpoints exist.

- [ ] ~~**2.5 `GET /api/v1/tags`**~~ — moved to v2 with filter-by-tag (D6).

- [x] **2.6 Manual smoke test**
  Load `blog-seed.sql` with `psql`, `mvnw.cmd spring-boot:run`, then hit the
  endpoints with `curl` + your `PUBLIC_API_KEY`, once per `Accept-Language`
  (`en`, `uk`, `ja`, `fr`, none). Check `Content-Language`/`Vary`, a
  draft-only slug (→ `NOT_FOUND`) and a post missing one locale
  (→ `TRANSLATION_NOT_AVAILABLE`). With `app.docs.public=true`, check Swagger
  UI at `/swagger-ui.html` renders them correctly.

---

## Phase 3 — Admin write API

Goal: create/edit/publish posts and manage tags via `/api/v1/admin/**`
(admin filter chain, `ADMIN_API_KEY`). Posts are addressed by numeric id (D13).

> Checked against the Phase 1–2 code on 2026-10-22, before starting. Added
> 3.0 (error details, entity fixes, locale path converter) and 3.3 (admin
> response DTOs), which the first draft left out; the later steps were
> renumbered.
>
> Status 2026-10-22: 3.0–3.7 done. Full suite 183/183 green, SpotBugs clean,
> smoke script 38/38 with `-Seed` (it now runs the 3.7 admin lifecycle).
>
> Deviations:
> - Time comes from one injected `java.time.Clock` bean (`shared.ClockConfig`):
>   JPA auditing, publish, soft delete and the error envelope's `timestamp` all
>   read it, and entity methods that change content take `Instant now`
>   explicitly. Tests replace it with a fixed clock (`@TestBean`). It ticks in
>   microseconds, the precision Postgres stores: with `Instant.now()`'s
>   nanoseconds, a write's response showed a different `publishedAt` than a
>   later read.
> - `GET /api/v1/admin/tags/{id}` added, so `Location` from `POST` resolves.
> - `UNKNOWN_TAG` details name the request position: `tagSlugs[1]`.
> - Field-level codes live in `shared.FieldErrorCode` (keys
>   `error.field.*`), checked by `ErrorCodeMessagesTest` like `ErrorCode`.
> - Figure classes are checked token by token (`image…`/`table…`): a single
>   regex with nested repetition tripped SpotBugs' ReDoS check.
> - Owasp sanitizer pinned to `20260924.2`.

Tests ride along with each step, as in Phase 2: write the matching 5.3
`MockMvc` cases and 5.4 unit tests as you go.

- [x] **3.0 Error details, entity fixes, locale path converter**
  - `ApiException` gets optional `details` (`List<ApiFieldError>`), and
    `handleApiException` passes them into the envelope (D21). Detail
    `message`s resolve through `MessageSource` like the top-level message:
    add `error.field.not_blank` and `error.field.unknown_tag` to all three
    message files.
  - Move the entity exceptions onto `ApiException` (D21):
    `PublishRequirementsNotMetException` → `400 VALIDATION_FAILED`, one
    `NOT_BLANK` detail per missing field (D9); `TranslationNotFoundException`
    → `404 NOT_FOUND`; `PostDeletedException` → `404 NOT_FOUND` (D23);
    `SlugFrozenException` → `409 SLUG_FROZEN`. Add the `ErrorCode`s
    `DUPLICATE_SLUG`, `SLUG_FROZEN` and `TAG_IN_USE` with keys in all three
    files (D17).
  - `Post.changeSlug`: an unchanged slug returns before the frozen check
    (D10).
  - `Post.softDelete`: keep the first `deletedAt` (D23).
  - `Post.translations` gets `@BatchSize(size = 50)` like `tags`; without it
    the admin list (D16) loads translations one post at a time.
  - `Tag`: add `changeSlug(slug)` and `replaceNames(Map<SupportedLocale,
    String>)`, which renames existing rows in place (`putName`) and removes
    the locales missing from the map (orphan removal deletes them). Never
    remove a locale's row and re-add it in the same save: Hibernate flushes
    inserts before deletes, so the new row would hit `uq_tag_locale`.
  - Case-insensitive `Converter<String, SupportedLocale>` for the `{locale}`
    path segment, registered in `LocaleConfig` (`shared`, next to
    `SupportedLocale`). An unknown value like `fr` becomes a type mismatch →
    `400 VALIDATION_FAILED` (D20).

- [x] **3.1 HTML sanitization**
  Add the OWASP Java HTML Sanitizer dependency. Create a `BodySanitizer`
  component whose policy allows what CKEditor 5 emits for blog content: `p`,
  headings, lists, `a`, `img`, `figure`/`figcaption`, tables, `blockquote`,
  `pre`/`code` (with `class="language-*"` on `code` for syntax highlighting),
  inline formatting. Images allow only `https` sources; links get
  `rel="nofollow noopener noreferrer"`. Only `body` is HTML: `title`,
  `excerpt` and the meta fields are plain text the frontend escapes. Unit
  test with a `<script>` payload, an `onerror` attribute and a
  `javascript:` link to confirm they're stripped, and a CKEditor sample to
  confirm it survives.

- [x] **3.2 Admin request DTOs + validation**
  `CreatePostRequest` (slug), `UpdatePostRequest` (slug),
  `SetPostTagsRequest` (tag slugs), `UpsertTranslationRequest` (title,
  excerpt, body, metaTitle, metaDescription), `UpsertTagRequest` (slug, color,
  names per locale). Use Bean Validation (`@NotBlank`, `@Size` matching the
  column lengths, `@Pattern` for slug format and `#RRGGBB` color); these flow
  through the existing `VALIDATION_FAILED` path. `body` is `TEXT`, so cap it
  with `@Size` (e.g. 200 000 characters) to reject runaway payloads.
  If `UpsertTagRequest.names` is a `Map<SupportedLocale, String>`, test that
  the lowercase keys (`"en"`) deserialize: `@JsonValue` may not apply to map
  keys. If it doesn't, use a record with one field per locale instead.

- [x] **3.3 Admin response DTOs**
  Records, with ids (D13) and every locale's content (D15):
  - `AdminPostResponse`: id, slug, createdAt, updatedAt, deletedAt, tags (as
    `AdminTagResponse`), and translations in `en, uk, ja` order (D18), each
    with locale, title, excerpt, body, metaTitle (raw, no D7 fallback),
    metaDescription, status, publishedAt and updatedAt. Every post write
    returns it, so each locale tab can refresh all statuses.
  - `AdminPostListItem` (D16): id, slug, updatedAt, deletedAt, and per
    translation its locale, status and updatedAt.
  - `AdminTagResponse`: id, slug, color, and every name with its locale.

- [x] **3.4 `PostAdminService`**
  All methods `@Transactional`; any unique-constraint race surfaces at commit
  as `DataIntegrityViolationException` → `409 CONFLICT` (3.6).
  - `create(slug)` → creates a `Post` with no translations. Pre-check
    `existsBySlug` → `409 DUPLICATE_SLUG` (D10).
  - `changeSlug(id, slug)` → no-op if unchanged; otherwise `409 SLUG_FROZEN`
    once published in any locale, `409 DUPLICATE_SLUG` if taken (D10).
  - `upsertTranslation(id, locale, dto)` → create-or-replace, running `body`
    through `BodySanitizer`. Live immediately if published (D11).
  - `publish(id, locale)` → D9 checks (`400` with details, 3.0),
    `status = PUBLISHED`, `published_at` set only if null (D8).
  - `unpublish(id, locale)` → `status = DRAFT`, `published_at` kept (D8).
  - For both: no translation row in that locale → `404 NOT_FOUND`.
  - `softDelete(id)` → sets `posts.deleted_at`; idempotent (D23).
  - `setTags(id, tagSlugs)` → replaces the tag set. Unknown tag slugs →
    `400 VALIDATION_FAILED`, one `UNKNOWN_TAG` detail per unknown slug.
  - Writes to a deleted post → `404 NOT_FOUND` (D23); an unknown id →
    `404 NOT_FOUND`.
  - `list(pageable, includeDeleted)` → fixed sort `updatedAt DESC, id DESC`,
    `?sort` ignored (D4, D16); needs a repository method that includes
    deleted posts next to `findByDeletedAtIsNull`. `get(id)` (draft preview:
    every translation, any status) also returns deleted posts (D23).

- [x] **3.5 `PostAdminController`** — under `/api/v1/admin/posts`:
  | Method & path | Action | Response |
  |---|---|---|
  | `GET /?page&size&deleted` | admin list (D16) | 200 page of `AdminPostListItem` |
  | `GET /{id}` | draft preview, all translations | 200 `AdminPostResponse` |
  | `POST /` | create | 201 + `Location` + `AdminPostResponse` |
  | `PUT /{id}` | change slug | 200 `AdminPostResponse` |
  | `DELETE /{id}` | soft delete | 204 |
  | `PUT /{id}/tags` | replace tags | 200 `AdminPostResponse` |
  | `PUT /{id}/translations/{locale}` | create/replace translation | 200 `AdminPostResponse` |
  | `POST /{id}/translations/{locale}/publish` | publish | 200 `AdminPostResponse` |
  | `POST /{id}/translations/{locale}/unpublish` | unpublish | 200 `AdminPostResponse` |

- [x] **3.6 `TagAdminService` + `TagAdminController`** — under
  `/api/v1/admin/tags`: `GET /` (all tags as `AdminTagResponse`, sorted by
  slug, not paginated: it's a small fixed vocabulary), `GET /{id}` (where
  `Location` points), `POST /` (201 + `Location`), `PUT /{id}` (replace slug,
  color and names), `DELETE /{id}` (204).
  - Tag slugs use the D10 format; pre-check `existsBySlug` →
    `409 DUPLICATE_SLUG` (an unchanged slug is fine). A duplicate tag name in
    a locale (`uq_tag_name_locale`) has no dedicated code in v1 and falls
    through to the generic `409 CONFLICT`.
  - ⚠️ Don't blanket-map `DataIntegrityViolationException` → `TAG_IN_USE` in
    `GlobalExceptionHandler` — every unique-constraint violation (duplicate
    slug, duplicate tag name) throws the same exception, and they'd all get
    mislabeled. Instead, pre-check in the service (`countPostsUsing`) and
    throw a domain `TagInUseException` (an `ApiException`, D21) mapped to 409
    `TAG_IN_USE`.
  - `countPostsUsing` also counts deleted posts, matching the FK: deleted
    posts keep their tags, so a tag used only by deleted posts still returns
    `TAG_IN_USE`. Accepted for v1.
  - Keep the FK `ON DELETE RESTRICT` as the race-condition backstop, and add a
    generic `DataIntegrityViolationException` → 409 `CONFLICT` handler as the
    fallback. It's not a Spring MVC exception, so a plain `@ExceptionHandler`
    is fine (D20's warning doesn't apply).

- [x] **3.7 Manual smoke test**
  Full lifecycle via `curl` with `ADMIN_API_KEY`: create post → add `en`
  translation → publish → confirm it appears in the public list → resend the
  same slug (expect 200) → change the slug (expect 409) → unpublish → confirm
  it disappears → publish again (same `publishedAt`) → soft-delete → confirm
  it's gone and the slug can't be reused → `GET /{id}` still shows it with
  `deletedAt` → a write to it returns 404 → deleting again returns 204 with
  the same `deletedAt`.

---

## Phase 4 — Caching

Goal: cache the public read paths in Redis and evict on admin writes
(D24–D26). Redis is already a dependency and running via `compose.yaml`.

> Reviewed against the code and Boot 4.0 / Spring Data Redis 4.0 on
> 2026-10-22, before starting. Changes from the first draft: `@EnableCaching`
> is always needed (`spring.cache.type` alone enables nothing); evictions wait
> for the commit; keys are explicit; the serializer uses Boot's mapper and
> reads strictly (D26); the TTL is 1 hour, not 5 minutes (D24); new steps 4.4
> (error handling) and 4.5 (Redis in the blog tests). The old 4.4 is now 4.6.
>
> Status 2026-10-22: 4.1–4.6 done. Full suite 203/203 green, Spotless and
> SpotBugs clean, smoke script 38/38 with `-Seed`. Live check against the
> compose stack: both caches fill (1-hour TTL), the detail key is gone as soon
> as an admin edit returns, the next public read shows the edit, and Grafana
> shows `cache_gets_total` hits and misses per cache.
>
> Deviations:
> - No `transactionAware()`. Its deferred evictions run in the transaction's
>   `afterCommit`, outside the `CacheErrorHandler`, so a failed one would fail
>   an already-committed write with a 500 (against D25). Instead
>   `@EnableCaching(order = HIGHEST_PRECEDENCE)` puts the caching advice
>   outside the transaction advice: the eviction runs after the commit, through
>   the error handler.
> - Immediate cache writes (`RedisCacheWriter` with `immediateWrites()`):
>   Spring Data Redis 4 writes asynchronously by default with Lettuce, so an
>   eviction could still be running when the admin response went out, and a
>   failed put or clear never reached the error handler.
> - `opentelemetry-logback-appender` pinned to `2.21.0-alpha`, the release
>   built on the OpenTelemetry API Boot 4.0.6 manages (1.55.0). Since May,
>   `2.27.0-alpha` had thrown `NoSuchMethodError`
>   (`LogRecordBuilder.setException`) on every log call that carried an
>   exception, so any logged 500 or cache warning failed the request.
>   `PostCacheRedisDownIntegrationTest` covers it.
> - Cache tests show a cached response by changing the row straight in the
>   database (no repository spy). Caches are cleared before each test by
>   `ClearCachesTestExecutionListener`, registered on each Redis-backed class.
> - The smoke script's `-Seed` also deletes the `blog-post-*` keys: the seed
>   bypasses the admin API, and Redis outlives the app.

- [x] **4.1 Enable Spring Cache**
  Add `spring-boot-starter-cache`: Boot 4 split cache autoconfiguration into
  its own module, and `spring-boot-starter-data-redis` alone doesn't bring it.
  `@EnableCaching` is required: Boot only configures a `CacheManager` when it's
  present, and then detects Redis on its own. The app-wide parts go in
  `shared` (`CacheConfig`): `@EnableCaching(order = HIGHEST_PRECEDENCE)`, so
  an eviction runs after the admin write's transaction commits (an eviction
  before the commit lets a public read cache the old data again until the
  TTL); the error handler (4.4); and immediate cache writes (see the
  deviations above). Properties: `spring.cache.redis.time-to-live=1h` (D24)
  and `spring.cache.redis.enable-statistics=true` (4.6).

- [x] **4.2 `@Cacheable` on read methods**
  Two caches, named as constants in `blog`: `blog-post-list` for
  `listVisible` and `blog-post-detail` for `getVisible` in
  `PostQueryService`. The `blog-` prefix keeps them apart from anything else
  in Redis later (for example the rate-limit counters in `project-plan.md`).
  Write the keys out in SpEL: `locale:page:size` and `slug:locale`, from the
  *resolved* locale (D3). The default key would use `Pageable.toString()`, which
  includes the ignored `sort` (D4), so one page would be stored once per sort
  value. `@EnableCaching` handles package-private methods in Spring 7, so the
  service stays as it is. Exceptions are never cached: 404s always reach
  Postgres.
  ⚠️ Cache the DTOs, never `Page`/`PageImpl` or entities — `PageImpl` has no
  default constructor and fails Jackson deserialization on Redis read, and
  entities drag lazy proxies into the serializer. `PostQueryService` already
  returns the `PageResponse<T>` record (D4, Phase 2), so cache that.
  ⚠️ Boot 4 uses Jackson 3, so the `Jackson2` serializers are the wrong
  family, and a generic serializer with default typing doesn't work for
  records: they're final, so no type info is written and a read comes back as
  a `LinkedHashMap` (`ClassCastException`). Register both caches with
  `withCacheConfiguration` in a `blog` `RedisCacheManagerBuilderCustomizer`,
  each with its own Jackson 3 `JacksonJsonRedisSerializer` and the exact
  `JavaType` (e.g. `PageResponse<PostSummaryResponse>`). Registering them up
  front is also what gets them metrics: Boot only adds metrics for caches that
  exist at startup. Build the serializer from Boot's `JsonMapper`, so cached
  JSON matches the API's (`SupportedLocale`, `Instant`), and turn on
  `FAIL_ON_MISSING_CREATOR_PROPERTIES` and `FAIL_ON_UNKNOWN_PROPERTIES` for
  it. Every record field is a constructor argument, so an old entry with a
  different shape fails to read instead of coming back with nulls (D26).

- [x] **4.3 `@CacheEvict` on writes**
  Every write method in `PostAdminService` and `TagAdminService` (anything not
  `readOnly`) clears both caches with `allEntries = true`, including `create`
  and `changeSlug`, which can't change public output today. One simple rule is
  easier to keep correct than deciding method by method. Put it on the
  methods, not the class: on the class it would also run on the admin reads
  (`list`, `get`). A meta-annotation (`@EvictsPublicPosts`) keeps that short.

- [x] **4.4 Cache error handling** (D25, D26)
  A `CacheErrorHandler` (from `CachingConfigurer.errorHandler()` in
  `shared.CacheConfig`) that never rethrows. A failed get counts as a miss:
  Spring runs the method and the put overwrites the entry. A
  `SerializationException` (an old shape, D26) is logged at `debug`; anything
  else (Redis unreachable) at `warn`. A failed put, evict or clear is logged at
  `warn`.

- [x] **4.5 Redis in the blog tests**
  `PostApiIntegrationTest`, `AdminPostApiIntegrationTest` and
  `AdminTagApiIntegrationTest` import only `PostgresContainerConfiguration`,
  so once caching is on their reads would try `localhost:6379`. Add a
  `RedisContainerConfiguration` (`@ServiceConnection`) and import it there.
  Clear every cache before each test (e.g. a `@BeforeEach` in a shared base or
  a test execution listener): `blog-seed.sql` truncates the tables, not Redis,
  so cached responses would leak between tests. The existing lifecycle tests
  (publish → shows in the public list, unpublish → gone) then check eviction
  end to end. New tests:
  - a cache **read**: the second call is served from Redis and equals the
    first, for both caches (e.g. `@MockitoSpyBean` on the repository sees one
    call);
  - an entry with an old shape (written straight into Redis) counts as a miss,
    and the response comes from Postgres and replaces it;
  - Redis unreachable → the public reads still return 200 (its own context,
    with Redis pointed at a closed port, so it doesn't disturb the shared
    container).

- [x] **4.6 Verify with logs/Grafana**
  Confirm cache hits/misses show up and that publishing a post is reflected
  immediately. datasource-micrometer only shows the queries that ran; the
  `cache.gets` hit/miss metrics need `spring.cache.redis.enable-statistics=true`.

---

## Phase 5 — Tests

Goal: lock in behavior with TestContainers-backed integration tests
(`TestcontainersConfiguration` / `TestApiApplication` already exist).

- [ ] **5.1 Repository tests** (if not already done in 1.7, expand coverage:
  pagination, locale filtering, soft-delete filtering, the unique constraints
  failing as expected).

- [ ] **5.2 Public API integration tests** (written alongside Phase 2; this
  step is the completeness check)
  `MockMvc` + real Postgres/Redis containers, data from `blog-seed.sql`: list
  posts (empty, populated, paginated, default size 10, `size` capped at 50,
  page past the end → empty), locale resolution (`uk`, `uk-UA`, `fr` → en, no
  header → en), `Content-Language`/`Vary` headers (on errors too), get single
  post (found with `availableLocales` in `en, uk, ja` order, unknown or
  uppercase slug → `NOT_FOUND`, draft-only → `NOT_FOUND`, not in locale →
  `TRANSLATION_NOT_AVAILABLE`, deleted → `NOT_FOUND`), tag name fallback and
  slug order, `POST /api/v1/posts` → 405 `UNSUPPORTED_REQUEST`,
  missing/invalid `PUBLIC_API_KEY` → 401.

- [ ] **5.3 Admin API integration tests** (written alongside Phase 3; this
  step is the completeness check)
  Full create → translate → publish → unpublish → re-publish (same
  `publishedAt`) → tag → soft-delete lifecycle. Publish without excerpt →
  `VALIDATION_FAILED` with a `NOT_BLANK` detail per missing field; unknown tag
  slug → `UNKNOWN_TAG` detail. Slug frozen after publish, resending the same
  slug is a 200 no-op, slug of a deleted post not reusable, tag in use can't
  be deleted. Deleted post: `GET /{id}` → 200 with `deletedAt`, writes → 404,
  second `DELETE` → 204 with the same `deletedAt` (D23). Unknown `{locale}`
  segment → 400, uppercase `EN` accepted. Error messages
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
  This is the frontend's integration reference, so document every parameter,
  request and response field (with the formats, limits and fallbacks from the
  decisions above), header, error `code` and field-level `code` on each
  endpoint, public and admin. Done after Phase 3 is implemented.
- [x] **6.2 Error message keys** — every new `ErrorCode` has keys in
  `messages.properties`, `messages_uk.properties` and `messages_ja.properties`.
  Done in Phase 2: `ErrorCodeMessagesTest` fails if any `ErrorCode` key is
  missing from any of the three files; since Phase 3 it checks the
  `FieldErrorCode` keys (`error.field.*`) too.
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
