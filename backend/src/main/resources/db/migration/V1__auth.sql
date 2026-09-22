-- Auth: accounts, refresh tokens, phone OTP challenges. See docs/TRD.md §6.1.

CREATE TABLE users (
    id                UUID PRIMARY KEY,
    full_name         TEXT,
    email             TEXT UNIQUE,
    phone             TEXT UNIQUE,
    password_hash     TEXT,
    google_subject    TEXT UNIQUE,
    role              TEXT        NOT NULL CHECK (role IN ('TREKKER', 'GUIDE', 'ADMIN')),
    status            TEXT        NOT NULL CHECK (status IN ('ACTIVE', 'DISABLED')),
    email_verified_at TIMESTAMPTZ,
    phone_verified_at TIMESTAMPTZ,
    created_at        TIMESTAMPTZ NOT NULL,
    updated_at        TIMESTAMPTZ NOT NULL,
    CONSTRAINT users_has_identity CHECK (email IS NOT NULL OR phone IS NOT NULL OR google_subject IS NOT NULL),
    CONSTRAINT users_email_lowercase CHECK (email = lower(email))
);

CREATE TABLE refresh_tokens (
    id             UUID PRIMARY KEY,
    user_id        UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    token_hash     TEXT        NOT NULL UNIQUE,
    expires_at     TIMESTAMPTZ NOT NULL,
    revoked_at     TIMESTAMPTZ,
    replaced_by_id UUID REFERENCES refresh_tokens (id),
    created_at     TIMESTAMPTZ NOT NULL
);

CREATE INDEX refresh_tokens_user_id_idx ON refresh_tokens (user_id);

CREATE TABLE otp_challenges (
    id          UUID PRIMARY KEY,
    phone       TEXT        NOT NULL,
    code_hash   TEXT        NOT NULL,
    expires_at  TIMESTAMPTZ NOT NULL,
    attempts    INT         NOT NULL DEFAULT 0,
    consumed_at TIMESTAMPTZ,
    created_at  TIMESTAMPTZ NOT NULL
);

CREATE INDEX otp_challenges_phone_created_at_idx ON otp_challenges (phone, created_at);
