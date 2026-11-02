# ADR-003: Sanitizing Post Bodies on Write

**Status:** Accepted
**Date:** 2026-11-02 (implemented 2026-10-22, blog plan Phase 3, step 3.1)

## Context

A post translation's body is HTML written in CKEditor 5 in the admin UI. The public website renders it as HTML, so anything in it reaches every reader's browser. Every other text field (title, excerpt, meta title and description) is plain text that the frontend escapes.

The author is trusted, but the content's path is not: the admin key could leak, the editor could be bypassed with a direct API call, and pasted content can carry markup CKEditor doesn't produce itself. A body with a `<script>`, an `onerror` attribute or a `javascript:` link would be stored XSS on the public site.

The question is where to clean the HTML, and against what.

## Decision

Sanitize every body on the server, once, when it is saved, with an allowlist policy (OWASP Java HTML Sanitizer) matched to what CKEditor 5 emits for blog content. Store and serve only the sanitized HTML.

- **On write, not on read:** `PUT /api/v1/admin/posts/{id}/translations/{locale}` runs the body through `BodySanitizer` before it is stored. Reads, and the public cache (ADR-002), serve the stored HTML as-is.
- **Allowlist, not blocklist:** paragraphs, `h2`–`h6`, lists, inline formatting, `blockquote`, `pre`/`code` (with `class="language-*"` for syntax highlighting), `figure`/`figcaption` (with CKEditor's `image…`/`table…` classes), tables, links and images. Anything else is removed, keeping its text: scripts, event handlers, inline styles, iframes, `h1` (the page renders the post title as its only `h1`).
- **Links and images:** links allow `https`, `http` and `mailto` and always get `rel="nofollow noopener noreferrer"`; images allow only `https` sources (no `data:` URIs, no mixed content).
- **An empty result is an error:** a body that is nothing but removed markup returns `400 VALIDATION_FAILED` with a `NOT_BLANK` detail on `body`, instead of saving an empty post.
- **The API documents it:** the request field's description in the OpenAPI spec says the body is sanitized and what is removed; the public response says the body is safe to render as-is.

## Consequences

**Benefits:**

- One place enforces the policy, regardless of how the request was made.
- Reads do no extra work, which keeps cached and uncached responses identical and cheap.
- The frontend can render the body without its own sanitizer for this content.
- What the author saves is what readers get: the admin response returns the sanitized body, so the editor can show the result right away.

**Tradeoffs:**

- **A policy change is not retroactive.** Tightening the allowlist doesn't clean bodies already stored; they stay as they were until re-saved. A security-driven change needs a one-off re-sanitization of every stored body. Loosening it doesn't restore markup already stripped; the author has to re-save from the original.
- **The original HTML is not kept.** Whatever the policy removed is gone.
- **The policy must follow CKEditor.** Enabling an editor feature that emits new markup (a new element, class or attribute) needs a matching policy change, or the markup is silently stripped on save.
- The sanitizer's output is normalized HTML, so it may differ textually from what the editor sent, even when nothing was removed.

## Alternatives Considered

**Sanitize on read:**
A policy change would apply to every body at once, and the original would be kept. But every uncached read would pay for sanitizing, and stored content would be unsafe by default: any new read path that forgot the sanitizer would serve raw HTML. Rejected: the write path is the single, narrow entry point.

**Sanitize on both write and read:**
Safest against a policy gap, at the cost of doing the work twice. Not justified while the only writer is the admin API and every body passes the same policy on the way in.

**Sanitize in the frontend only (e.g. DOMPurify):**
Leaves the API serving unsafe HTML to any other client, and relies on every rendering path remembering to sanitize. The frontend may still add it as defense in depth.

**Store Markdown, or the editor's JSON model, instead of HTML:**
Removes the raw-HTML problem by rendering from a safer format, but CKEditor 5's native output is HTML, and a different format would need conversion in both directions in the admin UI. Out of scope for v1.
