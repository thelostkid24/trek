-- The routes a guide leads (docs/TRD.md §6.25, §7.21.8): one row per trek or peak, in or out of our catalog, with
-- how often and when they last led it before Sahyātri (admin-set) and when they can lead it and a note (guide-set).
-- Replaces guide_prior_treks (V17) and guide_route_notes (V25), whose rows are copied here and which are dropped.

CREATE TABLE guide_routes (
    id                UUID PRIMARY KEY,
    guide_id          UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    track_id          UUID REFERENCES tracks (id) ON DELETE CASCADE,
    name              TEXT CHECK (length(name) BETWEEN 1 AND 80),
    kind              TEXT        NOT NULL DEFAULT 'TREK' CHECK (kind IN ('TREK', 'PEAK')),
    times_led         INT CHECK (times_led BETWEEN 1 AND 1000),
    last_led_year     INT CHECK (last_led_year BETWEEN 1950 AND 2100),
    last_led_month    INT CHECK (last_led_month BETWEEN 1 AND 12),
    most_recent       BOOLEAN     NOT NULL DEFAULT false,
    availability      TEXT        NOT NULL DEFAULT 'ASK'
        CHECK (availability IN ('ANY_TIME', 'FROM_DATE', 'NOT_OPEN', 'ASK')),
    season_from_month INT CHECK (season_from_month BETWEEN 1 AND 12),
    season_to_month   INT CHECK (season_to_month BETWEEN 1 AND 12),
    available_from    DATE,
    note              TEXT CHECK (length(note) BETWEEN 1 AND 200),
    position          INT         NOT NULL CHECK (position >= 0),
    created_at        TIMESTAMPTZ NOT NULL,
    updated_at        TIMESTAMPTZ NOT NULL,
    CHECK (track_id IS NOT NULL OR name IS NOT NULL),
    CHECK (last_led_month IS NULL OR last_led_year IS NOT NULL),
    CHECK ((season_from_month IS NULL) = (season_to_month IS NULL)),
    CHECK (season_from_month IS NULL OR availability = 'ANY_TIME'),
    CHECK ((available_from IS NOT NULL) = (availability = 'FROM_DATE'))
);

CREATE UNIQUE INDEX guide_routes_guide_track ON guide_routes (guide_id, track_id) WHERE track_id IS NOT NULL;
CREATE INDEX guide_routes_guide_idx ON guide_routes (guide_id, position);

-- Prior treks first, most-led first; then treks that only had a note.
INSERT INTO guide_routes (id, guide_id, track_id, times_led, note, position, created_at, updated_at)
SELECT gen_random_uuid(), p.guide_id, p.track_id, p.times, n.note,
       row_number() OVER (PARTITION BY p.guide_id ORDER BY p.times DESC, p.track_id) - 1, p.created_at, now()
FROM guide_prior_treks p
         LEFT JOIN guide_route_notes n ON n.guide_id = p.guide_id AND n.track_id = p.track_id;

INSERT INTO guide_routes (id, guide_id, track_id, note, position, created_at, updated_at)
SELECT gen_random_uuid(), n.guide_id, n.track_id, n.note,
       COALESCE((SELECT count(*) FROM guide_prior_treks p WHERE p.guide_id = n.guide_id), 0)
           + row_number() OVER (PARTITION BY n.guide_id ORDER BY n.track_id) - 1,
       n.updated_at, n.updated_at
FROM guide_route_notes n
WHERE NOT EXISTS (SELECT 1 FROM guide_prior_treks p WHERE p.guide_id = n.guide_id AND p.track_id = n.track_id);

DROP TABLE guide_route_notes;
DROP TABLE guide_prior_treks;
