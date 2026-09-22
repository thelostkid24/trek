-- Trek photos from past departures, shown on the trek page. See docs/TRD.md §6.8.
-- The row id is also the storage key (track-photos/<id>.jpg).

CREATE TABLE track_photos (
    id         UUID PRIMARY KEY,
    track_id   UUID        NOT NULL REFERENCES tracks (id) ON DELETE CASCADE,
    caption    TEXT        CHECK (char_length(caption) BETWEEN 1 AND 200),
    created_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX track_photos_track_idx ON track_photos (track_id, created_at);
