-- Photos and short videos on the guide profile (docs/TRD.md §6.21, §7.21.4). Photos and video posters are JPEGs at
-- guide-media/<id>.jpg; a video's bytes are at guide-videos/<id>, uploaded by the browser straight to storage.

CREATE TABLE guide_media (
    id          UUID PRIMARY KEY,
    guide_id    UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    kind        TEXT        NOT NULL CHECK (kind IN ('PHOTO', 'VIDEO')),
    caption     TEXT CHECK (length(caption) BETWEEN 1 AND 120),
    position    INT         NOT NULL CHECK (position >= 0),
    duration_s  INT CHECK (duration_s BETWEEN 1 AND 90),
    size_bytes  BIGINT      NOT NULL CHECK (size_bytes > 0),
    poster_key  UUID,
    status      TEXT        NOT NULL CHECK (status IN ('PENDING', 'READY')),
    created_at  TIMESTAMPTZ NOT NULL,
    CONSTRAINT guide_media_video CHECK (
        (kind = 'VIDEO' AND duration_s IS NOT NULL)
        OR (kind = 'PHOTO' AND duration_s IS NULL AND poster_key IS NULL AND status = 'READY'))
);

CREATE INDEX guide_media_guide ON guide_media (guide_id, position);
CREATE INDEX guide_media_pending ON guide_media (created_at) WHERE status = 'PENDING';
