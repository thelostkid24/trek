-- Who took a trek photo and on what terms we use it, so the trek page can credit it. See docs/TRD.md §6.14.
-- NULL = not recorded (photos uploaded before V18).

ALTER TABLE track_photos
    ADD COLUMN credit  TEXT CHECK (char_length(credit) BETWEEN 1 AND 100),
    ADD COLUMN licence TEXT CHECK (licence IN ('OURS', 'WITH_PERMISSION', 'CC_BY', 'CC_BY_SA', 'CC0', 'UNSPLASH'));
