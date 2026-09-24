-- Full trek page: more route facts, a richer itinerary, photo places, shared and per-trek content,
-- weekly snow reports, guide credentials and reviews. See docs/TRD.md §6.10.

ALTER TABLE tracks DROP CONSTRAINT tracks_difficulty_check;
ALTER TABLE tracks ADD CONSTRAINT tracks_difficulty_check
    CHECK (difficulty IN ('EASY', 'EASY_MODERATE', 'MODERATE', 'CHALLENGING'));

-- NULL = not stated yet; the trek page leaves the fact out.
ALTER TABLE tracks
    ADD COLUMN pickup_drop            TEXT,
    ADD COLUMN cloakroom              BOOLEAN,
    ADD COLUMN offloading             BOOLEAN,
    -- NULL with offloading = paid, price not fixed yet.
    ADD COLUMN offloading_price_paise BIGINT CHECK (offloading_price_paise > 0);

-- `summary` stays the day's heading ("Sankri to Juda ka Talab"); the rest is optional.
ALTER TABLE track_itinerary_days
    ADD COLUMN description      TEXT,
    ADD COLUMN distance_km      NUMERIC(4, 1) CHECK (distance_km > 0),
    ADD COLUMN start_altitude_m INT CHECK (start_altitude_m > 0),
    ADD COLUMN high_altitude_m  INT CHECK (high_altitude_m > 0),
    ADD COLUMN end_altitude_m   INT CHECK (end_altitude_m > 0),
    ADD COLUMN hours_min        NUMERIC(3, 1) CHECK (hours_min > 0),
    ADD COLUMN hours_max        NUMERIC(3, 1),
    ADD COLUMN route_note       TEXT,
    ADD CONSTRAINT track_itinerary_days_hours
        CHECK (hours_max IS NULL OR (hours_min IS NOT NULL AND hours_max >= hours_min));

ALTER TABLE track_photos
    ADD COLUMN place      TEXT CHECK (char_length(place) BETWEEN 1 AND 100),
    ADD COLUMN day_number INT CHECK (day_number BETWEEN 1 AND 7);

-- Lists and FAQs on the trek page. track_id NULL = shown on every trek, before that trek's own items.
CREATE TABLE trek_content_items (
    id         UUID PRIMARY KEY,
    track_id   UUID REFERENCES tracks (id) ON DELETE CASCADE,
    kind       TEXT        NOT NULL CHECK (kind IN
        ('INCLUDED', 'NOT_INCLUDED', 'SAFETY', 'SAFETY_CALLOUT', 'SAFETY_NOTE', 'FAQ', 'WHY_US')),
    position   INT         NOT NULL CHECK (position >= 0),
    badge      TEXT,
    title      TEXT,
    body       TEXT        NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT trek_content_items_position UNIQUE NULLS NOT DISTINCT (track_id, kind, position)
);

-- Weekly conditions from the trailhead, by an admin or a guide who leads the trek. Append-only history.
CREATE TABLE snow_reports (
    id            UUID PRIMARY KEY,
    track_id      UUID        NOT NULL REFERENCES tracks (id) ON DELETE CASCADE,
    reported_by   UUID        NOT NULL REFERENCES users (id),
    reported_on   DATE        NOT NULL,
    reported_from TEXT        NOT NULL,
    snowline_m    INT CHECK (snowline_m > 0),
    night_temp_c  INT CHECK (night_temp_c BETWEEN -60 AND 50),
    -- [{ "label": "Juda ka Talab", "value": "Frozen" }, …]
    conditions    JSONB       NOT NULL DEFAULT '[]',
    crowd_place   TEXT,
    crowd_tents   INT CHECK (crowd_tents >= 0),
    note          TEXT,
    has_photo     BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at    TIMESTAMPTZ NOT NULL,
    CONSTRAINT snow_reports_crowd CHECK ((crowd_tents IS NULL) = (crowd_place IS NULL))
);

CREATE INDEX snow_reports_track_idx ON snow_reports (track_id, reported_on DESC, created_at DESC);

-- What a trekker sees about a guide beyond the account. Years leading is worked out from leading_since.
CREATE TABLE guide_profiles (
    user_id              UUID PRIMARY KEY REFERENCES users (id) ON DELETE CASCADE,
    leading_since        INT CHECK (leading_since BETWEEN 1950 AND 2100),
    languages            TEXT,
    certification        TEXT,
    certification_number TEXT,
    quote                TEXT,
    created_at           TIMESTAMPTZ NOT NULL,
    updated_at           TIMESTAMPTZ NOT NULL
);

-- One review per paid booking, once its departure is completed. Averages are computed at read time.
CREATE TABLE reviews (
    id           UUID PRIMARY KEY,
    booking_id   UUID        NOT NULL UNIQUE REFERENCES bookings (id),
    user_id      UUID        NOT NULL REFERENCES users (id),
    departure_id UUID        NOT NULL REFERENCES departures (id),
    guide_id     UUID        NOT NULL REFERENCES users (id),
    track_id     UUID        NOT NULL REFERENCES tracks (id),
    rating       INT         NOT NULL CHECK (rating BETWEEN 1 AND 5),
    body         TEXT CHECK (char_length(body) BETWEEN 1 AND 2000),
    author_name  TEXT        NOT NULL,
    created_at   TIMESTAMPTZ NOT NULL,
    updated_at   TIMESTAMPTZ NOT NULL
);

CREATE INDEX reviews_guide_idx ON reviews (guide_id, created_at DESC);
