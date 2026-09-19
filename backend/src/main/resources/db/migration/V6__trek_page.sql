-- Trek page: route facts and the day-by-day itinerary. See docs/TRD.md §6.6.

ALTER TABLE tracks
    ADD COLUMN distance_km      NUMERIC(5, 1) CHECK (distance_km > 0),
    ADD COLUMN base_altitude_m  INT CHECK (base_altitude_m > 0),
    ADD COLUMN highest_camp_m   INT CHECK (highest_camp_m > 0),
    ADD COLUMN stay             TEXT,
    ADD COLUMN season_label     TEXT;

CREATE TABLE track_itinerary_days (
    id         UUID PRIMARY KEY,
    track_id   UUID NOT NULL REFERENCES tracks (id) ON DELETE CASCADE,
    day_number INT  NOT NULL CHECK (day_number BETWEEN 1 AND 7),
    summary    TEXT NOT NULL,
    UNIQUE (track_id, day_number)
);
