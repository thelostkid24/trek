-- Certification statuses and safety pledges (docs/TRD.md §6.20, §6.26, §7.21.3): a course the guide is still doing
-- shows "In course"; the two pledges an admin records from onboarding show "Agreed" and "Declared".

ALTER TABLE guide_certifications ADD COLUMN in_course BOOLEAN NOT NULL DEFAULT false;

CREATE TABLE guide_pledges (
    guide_id    UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    kind        TEXT        NOT NULL CHECK (kind IN ('NO_ALCOHOL', 'NO_SERIOUS_INCIDENTS')),
    recorded_at TIMESTAMPTZ NOT NULL,
    recorded_by UUID REFERENCES users (id) ON DELETE SET NULL,
    PRIMARY KEY (guide_id, kind)
);
