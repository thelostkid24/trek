-- Blog (docs/TRD.md §6.16, §7.19): admin-written posts filed under a category or one of its sub-categories (one
-- level deep, kept so by the service), each with its own photos. A post is public once published_at is set.

CREATE TABLE blog_categories (
    id         UUID PRIMARY KEY,
    parent_id  UUID REFERENCES blog_categories (id),
    name       TEXT        NOT NULL CHECK (char_length(name) BETWEEN 1 AND 60),
    slug       TEXT        NOT NULL UNIQUE CHECK (slug ~ '^[a-z0-9]+(-[a-z0-9]+)*$' AND char_length(slug) <= 80),
    created_at TIMESTAMPTZ NOT NULL,
    CHECK (parent_id <> id)
);
CREATE INDEX blog_categories_parent ON blog_categories (parent_id);

CREATE TABLE blog_posts (
    id             UUID PRIMARY KEY,
    category_id    UUID        NOT NULL REFERENCES blog_categories (id),
    slug           TEXT        NOT NULL UNIQUE CHECK (slug ~ '^[a-z0-9]+(-[a-z0-9]+)*$' AND char_length(slug) <= 100),
    title          TEXT        NOT NULL CHECK (char_length(title) BETWEEN 1 AND 150),
    excerpt        TEXT CHECK (char_length(excerpt) <= 300),
    body           TEXT        NOT NULL CHECK (char_length(body) <= 50000),
    cover_photo_id UUID,
    author_id      UUID        NOT NULL REFERENCES users (id),
    published_at   TIMESTAMPTZ,
    created_at     TIMESTAMPTZ NOT NULL,
    updated_at     TIMESTAMPTZ NOT NULL
);
CREATE INDEX blog_posts_category ON blog_posts (category_id);
CREATE INDEX blog_posts_published ON blog_posts (published_at DESC) WHERE published_at IS NOT NULL;

CREATE TABLE blog_photos (
    id         UUID PRIMARY KEY,
    post_id    UUID        NOT NULL REFERENCES blog_posts (id) ON DELETE CASCADE,
    caption    TEXT CHECK (char_length(caption) <= 200),
    created_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX blog_photos_post ON blog_photos (post_id, created_at);

ALTER TABLE blog_posts
    ADD CONSTRAINT blog_posts_cover FOREIGN KEY (cover_photo_id) REFERENCES blog_photos (id) ON DELETE SET NULL;
