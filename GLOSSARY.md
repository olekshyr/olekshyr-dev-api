# olekshyr personal site — Blog

The blog is the only content the API serves: posts written in several languages, published per language, and grouped by tags.

## Language

**Locale**:
One of the site's supported languages: `en` (default), `uk`, `ja`.
_Avoid_: language code, `ua`

**Post**:
A single piece of writing, identified by its slug, existing in one or more locales.
_Avoid_: article, entry

**Translation**:
The content of a post in one locale (title, excerpt, body, SEO fields), with its own publication status.
_Avoid_: version, localization, variant

**Slug**:
The URL-safe identifier of a post, shared by all its translations; frozen once the post is first published.
_Avoid_: permalink, handle

**Draft**:
A translation that is not shown to visitors, whether never published or unpublished.
_Avoid_: unpublished (as a separate state)

**Published**:
A translation shown to visitors in its locale. Its first publication date never changes, even across unpublish and re-publish.
_Avoid_: live, released

**Visible in a locale**:
A post that is not deleted and whose translation for that locale is published. This is the only sense in which a post is "published".
_Avoid_: published post (ambiguous)

**Deleted post**:
A post hidden from visitors in every locale but kept in storage; its slug stays reserved forever.
_Avoid_: removed, archived

**Tag**:
A label from a fixed vocabulary that admins manage, identified by a slug, with a name per locale and an optional color.
_Avoid_: category, label, keyword

**Read time**:
Estimated minutes to read a translation, worked out from its body length and never stored.
_Avoid_: reading time, duration
