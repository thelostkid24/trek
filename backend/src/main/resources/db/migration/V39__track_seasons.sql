-- The seasons a trek runs in, for the Season filter on /treks, and its place in the home page's "This season" row
-- (docs/TRD.md §6, §7.9). A trek with no home_order isn't on the home page; admins set both in the trek form.

CREATE TABLE track_seasons (
    track_id UUID NOT NULL REFERENCES tracks (id) ON DELETE CASCADE,
    season   TEXT NOT NULL CHECK (season IN ('WINTER', 'SPRING', 'SUMMER', 'MONSOON', 'AUTUMN')),
    PRIMARY KEY (track_id, season)
);

ALTER TABLE tracks ADD COLUMN home_order INT CHECK (home_order BETWEEN 1 AND 99);
