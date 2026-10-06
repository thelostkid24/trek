-- How often a guide led a trek before Sahyātri, entered by an admin. The trek, departure and guide pages add the
-- departures they've completed with us. See docs/TRD.md §6.13 and §7.12.

CREATE TABLE guide_prior_treks (
    guide_id   UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    track_id   UUID        NOT NULL REFERENCES tracks (id) ON DELETE CASCADE,
    times      INT         NOT NULL CHECK (times BETWEEN 1 AND 1000),
    created_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (guide_id, track_id)
);
