-- Catalog: tracks, departures, audit log. See docs/TRD.md §6.3.

CREATE TABLE tracks (
    id             UUID PRIMARY KEY,
    slug           TEXT        NOT NULL UNIQUE CHECK (slug ~ '^[a-z0-9]+(-[a-z0-9]+)*$'),
    name           TEXT        NOT NULL,
    region         TEXT        NOT NULL,
    difficulty     TEXT        NOT NULL CHECK (difficulty IN ('EASY', 'MODERATE', 'CHALLENGING')),
    duration_days  INT         NOT NULL CHECK (duration_days BETWEEN 1 AND 7),
    max_altitude_m INT CHECK (max_altitude_m > 0),
    summary        TEXT        NOT NULL,
    description    TEXT        NOT NULL,
    meeting_point  TEXT        NOT NULL,
    created_at     TIMESTAMPTZ NOT NULL,
    updated_at     TIMESTAMPTZ NOT NULL
);

CREATE TABLE departures (
    id                 UUID PRIMARY KEY,
    track_id           UUID        NOT NULL REFERENCES tracks (id),
    guide_id           UUID        NOT NULL REFERENCES users (id),
    start_date         DATE        NOT NULL,
    end_date           DATE        NOT NULL,
    price_paise        BIGINT      NOT NULL CHECK (price_paise > 0),
    max_group_size     INT         NOT NULL CHECK (max_group_size BETWEEN 1 AND 6),
    seats_taken        INT         NOT NULL DEFAULT 0,
    status             TEXT        NOT NULL CHECK (status IN ('DRAFT', 'PUBLISHED', 'CANCELLED', 'EXPIRED', 'COMPLETED')),
    guide_share_bps    INT CHECK (guide_share_bps BETWEEN 0 AND 10000),
    published_at       TIMESTAMPTZ,
    cancelled_at       TIMESTAMPTZ,
    cancel_reason_code TEXT CHECK (cancel_reason_code IN ('WEATHER', 'PERMIT_DENIED', 'GUIDE_UNAVAILABLE', 'SAFETY')),
    cancel_reason_note TEXT,
    created_at         TIMESTAMPTZ NOT NULL,
    updated_at         TIMESTAMPTZ NOT NULL,
    CONSTRAINT departures_dates CHECK (end_date >= start_date),
    -- Law 2: seats sold never exceed the batch size.
    CONSTRAINT departures_seats CHECK (seats_taken BETWEEN 0 AND max_group_size),
    -- Law 7: guide share is frozen at publish.
    CONSTRAINT departures_share_frozen CHECK (status = 'DRAFT' OR (guide_share_bps IS NOT NULL AND published_at IS NOT NULL)),
    -- Law 4: a cancellation always carries a reason.
    CONSTRAINT departures_cancel_reason CHECK (
        (status = 'CANCELLED') = (cancelled_at IS NOT NULL AND cancel_reason_code IS NOT NULL AND cancel_reason_note IS NOT NULL))
);

CREATE INDEX departures_status_start_date_idx ON departures (status, start_date);
CREATE INDEX departures_track_id_idx ON departures (track_id);

CREATE TABLE audit_events (
    id          UUID PRIMARY KEY,
    actor_id    UUID REFERENCES users (id),
    action      TEXT        NOT NULL,
    entity_type TEXT        NOT NULL,
    entity_id   UUID        NOT NULL,
    data        JSONB       NOT NULL DEFAULT '{}',
    created_at  TIMESTAMPTZ NOT NULL
);

CREATE INDEX audit_events_entity_idx ON audit_events (entity_type, entity_id, created_at);
