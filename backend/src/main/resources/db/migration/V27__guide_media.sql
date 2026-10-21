-- Photos and YouTube videos on the guide profile (docs/TRD.md §6.21, §7.21.4). Photos are JPEGs at
-- guide-media/<id>.jpg; a video is a YouTube video id, played embedded from youtube-nocookie.com. No video bytes
-- are stored by us.

CREATE TABLE guide_media (
    id          UUID PRIMARY KEY,
    guide_id    UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    kind        TEXT        NOT NULL CHECK (kind IN ('PHOTO', 'VIDEO')),
    caption     TEXT CHECK (length(caption) BETWEEN 1 AND 120),
    position    INT         NOT NULL CHECK (position >= 0),
    youtube_id  TEXT CHECK (youtube_id ~ '^[A-Za-z0-9_-]{11}$'),
    created_at  TIMESTAMPTZ NOT NULL,
    CONSTRAINT guide_media_kind CHECK ((kind = 'VIDEO') = (youtube_id IS NOT NULL))
);

CREATE INDEX guide_media_guide ON guide_media (guide_id, position);
CREATE UNIQUE INDEX guide_media_youtube ON guide_media (guide_id, youtube_id) WHERE youtube_id IS NOT NULL;
