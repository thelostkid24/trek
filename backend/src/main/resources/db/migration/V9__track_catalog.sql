-- Public trek catalog (/treks). See docs/TRD.md §6.9.
-- A track with an upcoming published departure is always in the catalog; `listed` lets an admin
-- also show a track that has no dates yet ("dates coming soon").

ALTER TABLE tracks
    ADD COLUMN listed BOOLEAN NOT NULL DEFAULT FALSE;
