-- Trekker profile, profile photo, email change/verification, purpose-scoped OTPs. See docs/TRD.md §6.2.

CREATE TABLE trekker_profiles (
    user_id            UUID PRIMARY KEY REFERENCES users (id) ON DELETE CASCADE,
    date_of_birth      DATE,
    gender             TEXT CHECK (gender IN ('FEMALE', 'MALE', 'NON_BINARY', 'PREFER_NOT_TO_SAY')),
    home_city          TEXT,
    experience_level   TEXT CHECK (experience_level IN ('BEGINNER', 'INTERMEDIATE', 'EXPERIENCED')),
    bio                TEXT,
    emergency_name     TEXT,
    emergency_relation TEXT,
    emergency_phone    TEXT,
    blood_group        TEXT CHECK (blood_group IN ('A+', 'A-', 'B+', 'B-', 'AB+', 'AB-', 'O+', 'O-')),
    medical_notes      TEXT,
    created_at         TIMESTAMPTZ NOT NULL,
    updated_at         TIMESTAMPTZ NOT NULL,
    CONSTRAINT trekker_profiles_emergency_complete CHECK (
        (emergency_name IS NULL AND emergency_relation IS NULL AND emergency_phone IS NULL)
        OR (emergency_name IS NOT NULL AND emergency_relation IS NOT NULL AND emergency_phone IS NOT NULL))
);

ALTER TABLE users ADD COLUMN avatar_key TEXT;

CREATE TABLE email_verifications (
    id          UUID PRIMARY KEY,
    user_id     UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    email       TEXT        NOT NULL CHECK (email = lower(email)),
    token_hash  TEXT        NOT NULL UNIQUE,
    expires_at  TIMESTAMPTZ NOT NULL,
    consumed_at TIMESTAMPTZ,
    created_at  TIMESTAMPTZ NOT NULL
);

CREATE INDEX email_verifications_user_id_created_at_idx ON email_verifications (user_id, created_at);

ALTER TABLE otp_challenges
    ADD COLUMN purpose TEXT NOT NULL DEFAULT 'LOGIN' CHECK (purpose IN ('LOGIN', 'PHONE_CHANGE'));

DROP INDEX otp_challenges_phone_created_at_idx;
CREATE INDEX otp_challenges_phone_purpose_created_at_idx ON otp_challenges (phone, purpose, created_at);
