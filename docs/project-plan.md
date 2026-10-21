# Project Plan — olekshyr personal site API

**Status:** Living document · **Last updated:** 2026-10-17

The overall scope, architecture, decisions and roadmap for the API. Blog-specific
work is in [`blog-implementation-plan.md`](blog-implementation-plan.md), the
blog data model is in [`erd-blog.md`](erd-blog.md), and architectural decisions
are in [`adr/`](adr/README.md).

---

## Vision

A personal site backed by a private Java/Spring API. It has two roles: a site
that actually gets used, and a public GitHub portfolio that shows how it is
built and run. Security is a primary concern on every surface.

## Scope

| Page | Source | Notes |
|---|---|---|
| Home / landing | Static in Next.js | May show latest posts via the blog list endpoint |
| Blog | **API** | The only API-backed feature; see the blog plan |
| Resume | Static in Next.js | Hardcoded on-page CV, contact info lives here; downloadable PDF/Word as static files |
| Projects | Static in Next.js | Links to pet projects |
| About, Uses | Static in Next.js (optional) | Short bio + profile links; personal stack and a Mermaid system diagram of this project |

**Dropped (2026-10-17):**
- the contact page/form, because the resume carries contact info;
- every page that needs a third-party API: Anime (MyAnimeList), Dota (Steam/OpenDota) and Music (Spotify), plus home-page hobby teasers and integration health checks.

**Out of scope:** visitor accounts, recruiting-focused flows, real-time chat,
and a browser-callable Java API.

---

## Architecture

**Browser → Next.js (Vercel) → Spring API (API key) → PostgreSQL + Redis**

- The Java API is **not** a public browser API. Only Next.js server-side code
  calls it, so there's **no CORS** from the browser to Java and the API keys
  never reach the browser.
- Two keys, strictly separated: `PUBLIC_API_KEY` for public routes and
  `ADMIN_API_KEY` for `/api/v1/admin/**` (see ADR-001). Next.js uses the public
  key for public server-side routes and the admin key for admin server-side
  routes and actions.
- **Admin UI** lives in the same Next.js app under `/admin/*`, kept apart from
  public pages with route groups. It's protected by middleware: GitHub OAuth via
  Auth.js, a signed-cookie session, and access limited to one GitHub user ID
  (`ADMIN_GITHUB_ID`). Java isn't involved in admin identity; it only checks the
  admin API key. `/admin` is never linked from the public header or footer.

## Decisions (locked)

| Decision | Choice |
|---|---|
| Repositories | Separate repos for the Next.js site and this API (not a monorepo) |
| Frontend hosting | Next.js on Vercel (public routes + `/admin`) |
| Blog authoring | WYSIWYG editor in admin (CKEditor leading option); HTML stored in Postgres via the API |
| HTML sanitization | OWASP Java HTML Sanitizer with a CKEditor-compatible allowlist |
| API versioning | URL path: `/api/v1/...` |
| Pagination | Offset-based: `?page=0&size=10`, `size` capped at 50 (wrapper shape and edge cases: see blog plan D4) |
| Tags | Entities with a join table |
| i18n | `en` (default), `uk`, `ja` for UI strings and blog content. URLs: `/blog/my-post`, `/uk/blog/my-post`, `/ja/blog/my-post` (English unprefixed). Slug shared across locales. **No fallback:** show "not available in this language" |
| Environments | Two only: local + prod (no staging). Secrets in git-ignored `.env` locally and host env vars in prod; never in properties files |

## Open decisions

| # | Decision | Needed by | Recommendation |
|---|---|---|---|
| 1 | API hosting provider | First deploy | Railway or Render for simplicity |
| 2 | Resume file hosting | Resume downloads | Static assets in Next.js, or S3-compatible bucket (e.g. Cloudflare R2) + CDN |
| 3 | Custom domain | Any time | Buy early; point Vercel and the API host at it |
| 4 | Blog comments | Later | None / Giscus |

---

## Security baseline

Applies to every surface: public pages, `/admin`, the REST API and infrastructure.

- **Transport and secrets:** HTTPS everywhere in prod; secrets only in env or secret stores, never in git.
- **Backend access:** server-to-server only, with the `X-API-Key` header; constant-time key comparison; stateless.
- **Input handling:** validate all untrusted input server-side; sanitize stored or rendered content (blog HTML, search queries).
- **Abuse resistance:** rate limits and payload size limits on public paths (Redis-backed counters if needed).
- **Observability:** logs must never contain secrets or full request bodies.
- **Dependencies:** keep Spring and Next patched; Dependabot or Renovate on a weekly cadence.

---

## Roadmap (non-blog work)

### Foundation
- [ ] **README:** purpose, architecture sketch, tech stack, prerequisites, how to run locally, env var table (names only), implemented vs planned.
- [ ] **CI runs the tests:** `.github/workflows/maven.yml` runs `mvn -B package`; switch it to `./mvnw verify` with Docker available so the Testcontainers tests run, and add a README badge.
- [ ] **Dependabot or Renovate:** weekly Maven dependency PRs.
- [ ] **Environment profiles:** `application-local.properties` / `application-prod.properties`; commit a `.env.example` documenting the required vars.
- [ ] **Logging conventions:** JSON at INFO in prod, plain text at DEBUG locally; trace IDs on every request; per-package levels; redaction of secrets.
- [ ] **Dockerfile:** multi-stage build (Maven → distroless or slim Temurin/GraalVM), exposed port, health check.
- [ ] **Database operations:** HikariCP pool config; backups (managed snapshots in prod, `pg_dump` locally); documented in the README.

### Deploy
- [ ] Restrict actuator to health and info, with liveness and readiness probes (see the TODO in `SecurityConfig` about limiting them to the internal network).
- [ ] Deploy the API (open decision #1) with managed Postgres and Redis; verify the health endpoint.
- [ ] Validate the end-to-end blog read path against prod with a seeded published post.
- [ ] **OpenAPI:** commit or publish the spec so the frontend can generate types from it.

### Later
- [ ] **CI extras:** deploy step, OpenAPI breaking-change check (oasdiff), JaCoCo coverage floor (e.g. 60% of lines).
- [ ] **Site search (v2):** Postgres full-text (`tsvector` + GIN index) over published posts; `GET /api/v1/search?q=...&page=0&size=10`; return snippets with highlights.
- [ ] **Media upload:** `POST /api/v1/admin/media` to S3-compatible storage, returning a CDN URL; validate type and size server-side. Unblocks the blog `cover_image` column (nullable, non-breaking migration).

### Ongoing
- Keep dependencies patched; prioritize security updates.
- Keep the OpenAPI spec current on every API change.
- Add tests for every new endpoint: happy path, error and auth.
- Keep the README and `/uses` honest about what's implemented vs planned.
- Review security posture: key rotation, rate-limit metrics, dependency CVEs, log redaction.
- **Frontend:** Lighthouse and axe-core before each major release, aiming for WCAG 2.1 AA. Add a privacy policy and cookie consent if analytics are ever introduced.

## Backlog / ideas

- RSS feed or newsletter announcements
- Scheduled publishing for blog posts
- Media library in admin
- Status page with dependency health
- Chatbot grounded in the site's public content (resume Q&A); experimental
- "Learning path so far" page
