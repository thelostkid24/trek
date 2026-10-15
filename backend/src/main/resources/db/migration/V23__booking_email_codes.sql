-- Checkout email codes: a booking's contact email must be proven by a 6-digit code sent to it. See docs/TRD.md §6.

CREATE TABLE booking_email_codes (
    id         UUID PRIMARY KEY,
    email      TEXT        NOT NULL,
    code_hash  TEXT        NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    attempts   INT         NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX booking_email_codes_email_created_at_idx ON booking_email_codes (email, created_at);
