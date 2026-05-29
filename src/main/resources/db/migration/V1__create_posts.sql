CREATE TABLE posts (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    slug       VARCHAR(255) NOT NULL UNIQUE,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    deleted_at TIMESTAMPTZ           DEFAULT NULL
);

CREATE INDEX idx_posts_deleted_at ON posts (deleted_at) WHERE deleted_at IS NULL;

CREATE TABLE post_translations (
    id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    post_id          BIGINT      NOT NULL REFERENCES posts (id) ON DELETE CASCADE,
    locale           VARCHAR(5)  NOT NULL CHECK (locale IN ('en', 'uk', 'ja')),
    title            VARCHAR(255) NOT NULL,
    excerpt          VARCHAR(500),
    body             TEXT        NOT NULL,
    meta_title       VARCHAR(255),
    meta_description VARCHAR(320),
    status           VARCHAR(16) NOT NULL DEFAULT 'DRAFT' CHECK (status IN ('DRAFT', 'PUBLISHED')),
    published_at     TIMESTAMPTZ,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_post_locale UNIQUE (post_id, locale)
);

CREATE INDEX idx_post_translations_list ON post_translations (locale, status, published_at DESC);

CREATE TABLE tags (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    slug       VARCHAR(255) NOT NULL UNIQUE,
    color      VARCHAR(7),
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE tag_translations (
    id      BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tag_id  BIGINT      NOT NULL REFERENCES tags (id) ON DELETE CASCADE,
    locale  VARCHAR(5)  NOT NULL CHECK (locale IN ('en', 'uk', 'ja')),
    name    VARCHAR(100) NOT NULL,
    CONSTRAINT uq_tag_locale UNIQUE (tag_id, locale),
    CONSTRAINT uq_tag_name_locale UNIQUE (locale, name)
);

CREATE TABLE post_tags (
    post_id BIGINT NOT NULL REFERENCES posts (id) ON DELETE CASCADE,
    tag_id  BIGINT NOT NULL REFERENCES tags (id) ON DELETE RESTRICT,
    PRIMARY KEY (post_id, tag_id)
);

CREATE INDEX idx_post_tags_tag_id ON post_tags (tag_id);
