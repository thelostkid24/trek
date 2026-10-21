-- The shareable guide profile (docs/TRD.md §6.19, §7.21): a readable URL, a cover photo, specialties, a note per
-- route and career milestones, all edited by the guide. Later parts of §7.21 take V26–V30.

ALTER TABLE guide_profiles
    ADD COLUMN slug      TEXT UNIQUE CHECK (slug ~ '^[a-z0-9]+(-[a-z0-9]+)*$' AND length(slug) <= 60),
    ADD COLUMN cover_key TEXT;

-- Every guide gets a profile row, so every guide has a slug.
INSERT INTO guide_profiles (user_id, created_at, updated_at)
SELECT u.id, now(), now()
FROM users u
WHERE u.role = 'GUIDE'
  AND NOT EXISTS (SELECT 1 FROM guide_profiles gp WHERE gp.user_id = u.id);

-- Slug from the name ("Shrey Rai" → shrey-rai); namesakes get -2, -3 … in sign-up order.
WITH base AS (
    SELECT gp.user_id,
           u.created_at,
           COALESCE(NULLIF(trim(BOTH '-' FROM left(
                   regexp_replace(lower(COALESCE(u.full_name, '')), '[^a-z0-9]+', '-', 'g'), 50)), ''), 'guide') AS b
    FROM guide_profiles gp
             JOIN users u ON u.id = gp.user_id
),
ranked AS (
    SELECT user_id, b, row_number() OVER (PARTITION BY b ORDER BY created_at, user_id) AS n
    FROM base
)
UPDATE guide_profiles gp
SET slug = CASE WHEN r.n = 1 THEN r.b ELSE r.b || '-' || r.n END
FROM ranked r
WHERE r.user_id = gp.user_id;

-- Slugs a guide used before; their links redirect to the current one. Never reused by another guide.
CREATE TABLE guide_slug_history (
    slug       TEXT PRIMARY KEY,
    guide_id   UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL
);

-- The specialties a guide can pick from, kept by admins.
CREATE TABLE guide_specialties (
    id         UUID PRIMARY KEY,
    name       TEXT        NOT NULL UNIQUE CHECK (length(name) BETWEEN 1 AND 40),
    position   INT         NOT NULL CHECK (position >= 0),
    created_at TIMESTAMPTZ NOT NULL
);

INSERT INTO guide_specialties (id, name, position, created_at)
VALUES (gen_random_uuid(), 'Winter & snow craft', 0, now()),
       (gen_random_uuid(), 'High passes', 1, now()),
       (gen_random_uuid(), 'First-time trekkers', 2, now()),
       (gen_random_uuid(), 'Women-only groups', 3, now()),
       (gen_random_uuid(), 'Families with children', 4, now()),
       (gen_random_uuid(), 'Local culture', 5, now()),
       (gen_random_uuid(), 'Birding', 6, now()),
       (gen_random_uuid(), 'Wildflowers', 7, now()),
       (gen_random_uuid(), 'Photography stops', 8, now()),
       (gen_random_uuid(), 'Summit pushes', 9, now());

-- Up to eight per guide, in the guide's order.
CREATE TABLE guide_profile_specialties (
    guide_id     UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    specialty_id UUID NOT NULL REFERENCES guide_specialties (id) ON DELETE CASCADE,
    position     INT  NOT NULL CHECK (position BETWEEN 0 AND 7),
    PRIMARY KEY (guide_id, specialty_id)
);

-- A line in the guide's words about a route they lead ("knows which campsites stay out of the wind").
CREATE TABLE guide_route_notes (
    guide_id   UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    track_id   UUID        NOT NULL REFERENCES tracks (id) ON DELETE CASCADE,
    note       TEXT        NOT NULL CHECK (length(note) BETWEEN 1 AND 200),
    updated_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (guide_id, track_id)
);

-- "Career in the mountains": a year and a line, oldest first.
CREATE TABLE guide_milestones (
    id         UUID PRIMARY KEY,
    guide_id   UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    year       INT         NOT NULL CHECK (year BETWEEN 1950 AND 2100),
    text       TEXT        NOT NULL CHECK (length(text) BETWEEN 1 AND 140),
    position   INT         NOT NULL CHECK (position >= 0),
    created_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX guide_milestones_guide_idx ON guide_milestones (guide_id, position);
