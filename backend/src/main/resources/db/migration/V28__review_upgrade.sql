-- Reviews v2: sub-ratings, tags, reviews from a guide's own trekkers, guide replies, reports and admin hiding.
-- See docs/TRD.md §6.22 and §7.21.5.

-- BOOKING = a confirmed booking on a completed departure ("Verified trek"); OWN_TREKKER = someone who trekked with
-- the guide off-platform and proved an email or phone by code ("Group verified"). Only BOOKING reviews feed any
-- rating, count or bar.
ALTER TABLE reviews
    ADD COLUMN source               TEXT NOT NULL DEFAULT 'BOOKING' CHECK (source IN ('BOOKING', 'OWN_TREKKER')),
    ADD COLUMN reviewer_email       TEXT CHECK (char_length(reviewer_email) BETWEEN 3 AND 254),
    ADD COLUMN reviewer_phone       TEXT CHECK (char_length(reviewer_phone) BETWEEN 8 AND 20),
    ADD COLUMN trek_name            TEXT CHECK (char_length(trek_name) BETWEEN 1 AND 100),
    -- Optional 1..5 sub-ratings; NULL = not answered.
    ADD COLUMN safety_rating        INT CHECK (safety_rating BETWEEN 1 AND 5),
    ADD COLUMN route_rating         INT CHECK (route_rating BETWEEN 1 AND 5),
    ADD COLUMN communication_rating INT CHECK (communication_rating BETWEEN 1 AND 5),
    ADD COLUMN care_rating          INT CHECK (care_rating BETWEEN 1 AND 5),
    -- The guide's one public reply.
    ADD COLUMN reply_body           TEXT CHECK (char_length(reply_body) BETWEEN 1 AND 1000),
    ADD COLUMN reply_created_at     TIMESTAMPTZ,
    ADD COLUMN reply_updated_at     TIMESTAMPTZ,
    -- Admin moderation: set = not shown anywhere public. Every change is in audit_events.
    ADD COLUMN hidden_at            TIMESTAMPTZ,
    ADD COLUMN hidden_reason        TEXT CHECK (char_length(hidden_reason) BETWEEN 1 AND 500),
    ADD COLUMN reply_hidden_at      TIMESTAMPTZ,
    ADD COLUMN reply_hidden_reason  TEXT CHECK (char_length(reply_hidden_reason) BETWEEN 1 AND 500);

ALTER TABLE reviews
    ALTER COLUMN booking_id DROP NOT NULL,
    ALTER COLUMN user_id DROP NOT NULL,
    ALTER COLUMN departure_id DROP NOT NULL,
    ALTER COLUMN track_id DROP NOT NULL;

ALTER TABLE reviews ADD CONSTRAINT reviews_source_shape CHECK (
    (source = 'BOOKING' AND booking_id IS NOT NULL AND user_id IS NOT NULL AND departure_id IS NOT NULL
        AND track_id IS NOT NULL AND reviewer_email IS NULL AND reviewer_phone IS NULL)
    OR (source = 'OWN_TREKKER' AND booking_id IS NULL AND departure_id IS NULL
        AND (reviewer_email IS NULL) <> (reviewer_phone IS NULL)));
ALTER TABLE reviews ADD CONSTRAINT reviews_reply_shape CHECK (
    (reply_body IS NULL) = (reply_created_at IS NULL) AND (reply_body IS NULL) = (reply_updated_at IS NULL));
ALTER TABLE reviews ADD CONSTRAINT reviews_hidden_shape CHECK (
    (hidden_at IS NULL) = (hidden_reason IS NULL) AND (reply_hidden_at IS NULL) = (reply_hidden_reason IS NULL));

-- One review per guide and verified identity.
CREATE UNIQUE INDEX reviews_guide_email_uq ON reviews (guide_id, reviewer_email) WHERE reviewer_email IS NOT NULL;
CREATE UNIQUE INDEX reviews_guide_phone_uq ON reviews (guide_id, reviewer_phone) WHERE reviewer_phone IS NOT NULL;

-- "What stood out?" — a fixed list of codes (ReviewTag).
CREATE TABLE review_tags (
    review_id UUID NOT NULL REFERENCES reviews (id) ON DELETE CASCADE,
    tag       TEXT NOT NULL CHECK (tag IN ('FELT_SAFE', 'KNEW_THE_ROUTE', 'GOOD_PACE', 'GREAT_FOOD',
                                           'KNEW_THE_VILLAGES', 'CALM_IN_BAD_WEATHER')),
    PRIMARY KEY (review_id, tag)
);

-- A guide flags a review for admins. One open report per review and guide; admin action resolves them.
CREATE TABLE review_reports (
    id          UUID PRIMARY KEY,
    review_id   UUID        NOT NULL REFERENCES reviews (id) ON DELETE CASCADE,
    reporter_id UUID        NOT NULL REFERENCES users (id),
    reason      TEXT        NOT NULL CHECK (char_length(reason) BETWEEN 1 AND 500),
    created_at  TIMESTAMPTZ NOT NULL,
    resolved_at TIMESTAMPTZ
);
CREATE UNIQUE INDEX review_reports_open_uq ON review_reports (review_id, reporter_id) WHERE resolved_at IS NULL;
CREATE INDEX review_reports_open_idx ON review_reports (created_at DESC) WHERE resolved_at IS NULL;

-- Phone codes for off-platform reviewers.
ALTER TABLE otp_challenges DROP CONSTRAINT otp_challenges_purpose_check;
ALTER TABLE otp_challenges ADD CONSTRAINT otp_challenges_purpose_check
    CHECK (purpose IN ('LOGIN', 'PHONE_CHANGE', 'REVIEW'));
