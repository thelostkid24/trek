-- Forgot password: emailed single-use reset links. See docs/TRD.md §6.12, §7.2.

CREATE TABLE password_resets (
    id          UUID PRIMARY KEY,
    user_id     UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    email       TEXT        NOT NULL,
    token_hash  TEXT        NOT NULL UNIQUE,
    expires_at  TIMESTAMPTZ NOT NULL,
    consumed_at TIMESTAMPTZ,
    created_at  TIMESTAMPTZ NOT NULL
);

CREATE INDEX password_resets_user_id_created_at_idx ON password_resets (user_id, created_at);
