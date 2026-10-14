-- Planned dates (docs/TRD.md §6.17, §7.20): dates a trek will run whose guide isn't confirmed yet. Shown on the trek
-- page as "Guide: to be updated" and never bookable; a real departure (which always has a guide, law 3) takes over
-- once it's published on the same start date. The end date is worked out from the trek's length.

CREATE TABLE track_planned_dates (
    id         UUID PRIMARY KEY,
    track_id   UUID        NOT NULL REFERENCES tracks (id) ON DELETE CASCADE,
    start_date DATE        NOT NULL,
    note       TEXT CHECK (char_length(note) BETWEEN 1 AND 60),
    created_at TIMESTAMPTZ NOT NULL,
    UNIQUE (track_id, start_date)
);
