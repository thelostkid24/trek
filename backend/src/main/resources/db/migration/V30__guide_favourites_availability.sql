-- Favourites and availability (docs/TRD.md §6.24, §7.21.7): a trekker's private list of saved guides, and the date
-- ranges a guide marks as free to lead, which only admins see.

CREATE TABLE saved_guides (
    user_id    UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    guide_id   UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (user_id, guide_id),
    CHECK (user_id <> guide_id)
);

CREATE INDEX saved_guides_user_created ON saved_guides (user_id, created_at DESC);

-- Ranges of one guide never overlap (service, under a lock on the guide's user row). Past ranges stay.
CREATE TABLE guide_availability (
    id         UUID PRIMARY KEY,
    guide_id   UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    start_date DATE        NOT NULL,
    end_date   DATE        NOT NULL,
    note       TEXT CHECK (length(note) BETWEEN 1 AND 120),
    created_at TIMESTAMPTZ NOT NULL,
    CHECK (end_date >= start_date)
);

CREATE INDEX guide_availability_guide_start ON guide_availability (guide_id, start_date);
CREATE INDEX guide_availability_end ON guide_availability (end_date);
