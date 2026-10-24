-- Reviews from a guide's own trekkers no longer prove an email or phone (docs/TRD.md §6.29, §7.21.5): each one waits
-- for an admin to approve it before it shows. Also an optional "Other" answer to "What stood out?".

-- When an admin approved it; null = waiting. BOOKING reviews are approved as they're written. Every review that
-- exists now was already live, so it counts as approved from when it was written.
ALTER TABLE reviews ADD COLUMN approved_at TIMESTAMPTZ;
UPDATE reviews SET approved_at = created_at;
ALTER TABLE reviews ADD CONSTRAINT reviews_approved_shape CHECK (source = 'OWN_TREKKER' OR approved_at IS NOT NULL);

-- What the reviewer typed after picking "Other", shown with the review.
ALTER TABLE reviews ADD COLUMN other_tag TEXT CHECK (char_length(other_tag) BETWEEN 1 AND 60);

-- OWN_TREKKER rows may now carry no contact; older rows keep theirs (at most one).
ALTER TABLE reviews DROP CONSTRAINT reviews_source_shape;
ALTER TABLE reviews ADD CONSTRAINT reviews_source_shape CHECK (
    (source = 'BOOKING' AND booking_id IS NOT NULL AND user_id IS NOT NULL AND departure_id IS NOT NULL
        AND track_id IS NOT NULL AND reviewer_email IS NULL AND reviewer_phone IS NULL)
    OR (source = 'OWN_TREKKER' AND booking_id IS NULL AND departure_id IS NULL
        AND (reviewer_email IS NULL OR reviewer_phone IS NULL)));

CREATE INDEX reviews_waiting ON reviews (created_at DESC) WHERE approved_at IS NULL;
