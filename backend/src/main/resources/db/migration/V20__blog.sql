-- Blog (docs/TRD.md §6.16, §7.19). Ten fixed categories, each with sub-categories (one level deep); every post is
-- filed under exactly one sub-category, kept so by the service. A post is public once published_at is set. Post and
-- category slugs share the /blog/<slug> namespace, so neither may take the other's (service + tests).

CREATE TABLE blog_categories (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    parent_id   UUID REFERENCES blog_categories (id),
    name        TEXT        NOT NULL CHECK (char_length(name) BETWEEN 1 AND 60),
    slug        TEXT        NOT NULL UNIQUE CHECK (slug ~ '^[a-z0-9]+(-[a-z0-9]+)*$' AND char_length(slug) <= 80),
    description TEXT CHECK (char_length(description) <= 300),
    position    INT         NOT NULL DEFAULT 0,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK (parent_id <> id)
);
CREATE INDEX blog_categories_parent ON blog_categories (parent_id);

CREATE TABLE blog_posts (
    id             UUID PRIMARY KEY,
    category_id    UUID        NOT NULL REFERENCES blog_categories (id),
    slug           TEXT        NOT NULL UNIQUE CHECK (slug ~ '^[a-z0-9]+(-[a-z0-9]+)*$' AND char_length(slug) <= 100),
    title          TEXT        NOT NULL CHECK (char_length(title) BETWEEN 1 AND 150),
    excerpt        TEXT CHECK (char_length(excerpt) <= 160),
    body           TEXT        NOT NULL CHECK (char_length(body) <= 50000),
    cover_photo_id UUID,
    cover_caption  TEXT CHECK (char_length(cover_caption) <= 200),
    cover_taken_on DATE,
    author_id      UUID        NOT NULL REFERENCES users (id),
    -- Shown as the byline when set (e.g. a guide who wrote it); otherwise the author's account name.
    author_name    TEXT CHECK (char_length(author_name) <= 100),
    faqs           JSONB       NOT NULL DEFAULT '[]',
    schema_type    TEXT        NOT NULL DEFAULT 'ARTICLE' CHECK (schema_type IN ('ARTICLE', 'FAQ_PAGE', 'HOW_TO')),
    -- Admin only; never in a public response.
    research_notes TEXT CHECK (char_length(research_notes) <= 20000),
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

-- "Related treks": which treks a post is about (none for general posts like a gear guide).
CREATE TABLE blog_post_treks (
    post_id  UUID NOT NULL REFERENCES blog_posts (id) ON DELETE CASCADE,
    track_id UUID NOT NULL REFERENCES tracks (id) ON DELETE CASCADE,
    PRIMARY KEY (post_id, track_id)
);
CREATE INDEX blog_post_treks_track ON blog_post_treks (track_id);

-- A published post's URL never breaks: a slug it gave up redirects to the post.
CREATE TABLE blog_slug_redirects (
    old_slug   TEXT PRIMARY KEY,
    post_id    UUID        NOT NULL REFERENCES blog_posts (id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL
);

-- The ten categories and their sub-categories (the same for every trek).
CREATE TEMPORARY TABLE seed (pos INT, slug TEXT, name TEXT, subs TEXT[][]);
INSERT INTO seed VALUES
 (1, 'himalayan-treks', 'Himalayan Treks', ARRAY[
   ['trek-guides', 'Trek guides'], ['routes-and-itineraries', 'Routes & itineraries'],
   ['difficulty-and-grading', 'Difficulty & grading'], ['trek-comparisons', 'Trek comparisons'],
   ['which-trek', 'Which trek should you do']]),
 (2, 'plan-your-trek', 'Plan Your Trek', ARRAY[
   ['best-time-and-season', 'Best time & season'], ['permits-and-documents', 'Permits & documents'],
   ['where-to-stay', 'Where to stay'], ['trekker-budget', 'Trekker budget'],
   ['choosing-an-operator', 'Booking & choosing an operator'], ['trip-planning-basics', 'Trip planning basics']]),
 (3, 'gear-fitness-packing', 'Gear, Fitness & Packing', ARRAY[
   ['packing-lists', 'Packing lists'], ['gear-and-equipment', 'Gear & equipment'],
   ['renting-vs-buying', 'Renting vs buying'], ['fitness-and-training', 'Fitness & training']]),
 (4, 'altitude-and-safety', 'Altitude & Safety', ARRAY[
   ['altitude-sickness', 'Altitude sickness & acclimatisation'], ['first-aid-and-emergencies', 'First aid & emergencies'],
   ['wildlife-safety', 'Wildlife safety'], ['insurance-and-evacuation', 'Insurance & evacuation'],
   ['womens-health', 'Women''s health on trek'], ['medical-conditions', 'Trekking with a medical condition']]),
 (5, 'life-on-the-trail', 'Life on the Trail', ARRAY[
   ['food-and-water', 'Food & water'], ['hygiene', 'Toilets, hygiene & washing'],
   ['camping-and-sleeping', 'Camping, tents & sleeping'], ['network-and-electricity', 'Network & electricity'],
   ['weather-and-cold', 'Weather & cold'], ['trail-etiquette', 'Trail etiquette']]),
 (6, 'trekking-for-beginners', 'Trekking for Beginners', ARRAY[
   ['first-time-trekkers', 'First-time trekkers'], ['solo-trekkers', 'Solo trekkers'], ['solo-women', 'Solo women'],
   ['parents-and-seniors', 'Parents & seniors'], ['families-and-children', 'Families & children'],
   ['body-and-fitness', 'Body & fitness'], ['groups-and-friends', 'Groups & friends'],
   ['after-illness-or-injury', 'Trekking after illness or injury']]),
 (7, 'snow-and-trail-updates', 'Snow & Trail Updates', ARRAY[
   ['snow-reports', 'Snow reports'], ['weekly-conditions', 'Weekly conditions'], ['crowd-levels', 'Crowd levels'],
   ['road-and-access', 'Road & access status'], ['season-wrap-ups', 'Season wrap-ups']]),
 (8, 'villages-and-culture', 'Villages & Mountain Culture', ARRAY[
   ['guide-profiles', 'Guide profiles'], ['base-villages', 'Base villages & valleys'], ['village-life', 'Village life'],
   ['culture-and-mythology', 'Culture & mythology'], ['wildlife-and-forests', 'Wildlife, forests & flora'],
   ['photo-essays', 'Photo essays']]),
 (9, 'responsible-trekking', 'Responsible Trekking', ARRAY[
   ['leave-no-trace', 'Waste & leave no trace'], ['overcrowding', 'Overcrowding'],
   ['rules-and-regulation', 'Rules & regulation'], ['conservation', 'Conservation'],
   ['local-economy', 'The local economy']]),
 (10, 'trek-experiences', 'Trek Experiences', ARRAY[
   ['trek-reports', 'Trek reports'], ['trekker-stories', 'Trekker stories'],
   ['comeback-stories', 'Comeback stories & the held seat'], ['news-and-new-treks', 'News & new treks']]);

INSERT INTO blog_categories (parent_id, name, slug, position)
SELECT NULL, name, slug, pos FROM seed;

INSERT INTO blog_categories (parent_id, name, slug, position)
SELECT c.id, s.subs[i][2], s.subs[i][1], i
FROM seed s
JOIN blog_categories c ON c.slug = s.slug
CROSS JOIN LATERAL generate_subscripts(s.subs, 1) AS i;

DROP TABLE seed;
