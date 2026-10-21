-- Blog fixture for the public API tests (@Sql) and the manual smoke test (psql).
-- WARNING: wipes every blog table first. Never run against a database you care about.
--
-- What's in it:
--   hello-world    en + uk published, ja draft; tags java, spring
--   second-post    en published (newest en); tag life
--   japanese-only  ja published; tag spring (no ja name, so name falls back to the slug)
--   draft-only     en draft only (not visible anywhere)
--   deleted-post   en published but soft-deleted

TRUNCATE post_tags, post_translations, posts, tag_translations, tags RESTART IDENTITY CASCADE;

INSERT INTO tags (slug, color) VALUES
    ('java', '#b07219'),
    ('spring', '#6db33f'),
    ('life', NULL);

INSERT INTO tag_translations (tag_id, locale, name)
SELECT t.id, v.locale, v.name
FROM (VALUES
    ('java', 'en', 'Java'),
    ('java', 'uk', 'Java'),
    ('java', 'ja', 'Java'),
    ('spring', 'en', 'Spring'),
    ('spring', 'uk', 'Spring'),
    ('life', 'en', 'Life'),
    ('life', 'uk', 'Життя'),
    ('life', 'ja', '生活')
) AS v (slug, locale, name)
JOIN tags t ON t.slug = v.slug;

INSERT INTO posts (slug, deleted_at) VALUES
    ('hello-world', NULL),
    ('second-post', NULL),
    ('japanese-only', NULL),
    ('draft-only', NULL),
    ('deleted-post', '2026-02-15T00:00:00Z');

INSERT INTO post_translations
    (post_id, locale, title, excerpt, body, meta_title, meta_description, status, published_at)
SELECT p.id, v.locale, v.title, v.excerpt, v.body, v.meta_title, v.meta_description, v.status, v.published_at::timestamptz
FROM (VALUES
    ('hello-world', 'en', 'Hello, world', 'The first post.', '<p>Hello <strong>world</strong>.</p>', NULL, 'The first post on the blog.', 'PUBLISHED', '2026-01-10T09:00:00Z'),
    ('hello-world', 'uk', 'Привіт, світе', 'Перший допис.', '<p>Привіт, <strong>світе</strong>.</p>', 'Привіт, світе | Блог', 'Перший допис у блозі.', 'PUBLISHED', '2026-01-12T09:00:00Z'),
    ('hello-world', 'ja', 'こんにちは、世界', NULL, '<p>こんにちは。</p>', NULL, NULL, 'DRAFT', NULL),
    ('second-post', 'en', 'Second post', 'Another one.', '<p>More words here.</p>', 'Second | Blog', 'The second post.', 'PUBLISHED', '2026-02-01T09:00:00Z'),
    ('japanese-only', 'ja', '日本語だけ', '日本語の記事。', '<p>日本語の本文です。</p>', NULL, '日本語だけの記事。', 'PUBLISHED', '2026-03-01T09:00:00Z'),
    ('draft-only', 'en', 'Work in progress', NULL, '<p>Not ready.</p>', NULL, NULL, 'DRAFT', NULL),
    ('deleted-post', 'en', 'Gone', 'Deleted.', '<p>Gone.</p>', NULL, 'Deleted post.', 'PUBLISHED', '2026-01-20T09:00:00Z')
) AS v (slug, locale, title, excerpt, body, meta_title, meta_description, status, published_at)
JOIN posts p ON p.slug = v.slug;

INSERT INTO post_tags (post_id, tag_id)
SELECT p.id, t.id
FROM (VALUES
    ('hello-world', 'spring'),
    ('hello-world', 'java'),
    ('second-post', 'life'),
    ('japanese-only', 'spring'),
    ('deleted-post', 'java')
) AS v (post_slug, tag_slug)
JOIN posts p ON p.slug = v.post_slug
JOIN tags t ON t.slug = v.tag_slug;
