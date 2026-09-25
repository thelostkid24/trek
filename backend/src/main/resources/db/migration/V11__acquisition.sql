-- Acquisition: where accounts and bookings come from, how people heard of us, marketing consent, last seen.
-- See docs/TRD.md §6.11. NULL everywhere = not captured (accounts and bookings made before this migration).

-- First touch: the visit that first brought this person to the site. Written once, when the account is created.
ALTER TABLE users
    ADD COLUMN signup_method                 TEXT CHECK (signup_method IN ('EMAIL', 'PHONE', 'GOOGLE', 'GUEST_CHECKOUT')),
    ADD COLUMN utm_source                    TEXT CHECK (char_length(utm_source) BETWEEN 1 AND 200),
    ADD COLUMN utm_medium                    TEXT CHECK (char_length(utm_medium) BETWEEN 1 AND 200),
    ADD COLUMN utm_campaign                  TEXT CHECK (char_length(utm_campaign) BETWEEN 1 AND 200),
    ADD COLUMN utm_term                      TEXT CHECK (char_length(utm_term) BETWEEN 1 AND 200),
    ADD COLUMN utm_content                   TEXT CHECK (char_length(utm_content) BETWEEN 1 AND 200),
    ADD COLUMN gclid                         TEXT CHECK (char_length(gclid) BETWEEN 1 AND 500),
    ADD COLUMN fbclid                        TEXT CHECK (char_length(fbclid) BETWEEN 1 AND 500),
    ADD COLUMN referrer                      TEXT CHECK (char_length(referrer) BETWEEN 1 AND 1000),
    ADD COLUMN landing_path                  TEXT CHECK (char_length(landing_path) BETWEEN 1 AND 500),
    ADD COLUMN first_seen_at                 TIMESTAMPTZ,
    ADD COLUMN device_type                   TEXT CHECK (device_type IN ('MOBILE', 'TABLET', 'DESKTOP')),
    ADD COLUMN heard_from                    TEXT CHECK (heard_from IN ('INSTAGRAM', 'YOUTUBE', 'GOOGLE_SEARCH',
                                                                        'FRIEND_FAMILY', 'WHATSAPP_GROUP',
                                                                        'BLOG_FORUM', 'OTHER')),
    ADD COLUMN heard_from_note               TEXT CHECK (char_length(heard_from_note) BETWEEN 1 AND 200),
    -- Bumped on sign-in and token refresh, at most once an hour.
    ADD COLUMN last_seen_at                  TIMESTAMPTZ,
    -- NULL = no consent. Every change is also in audit_events.
    ADD COLUMN marketing_email_consent_at    TIMESTAMPTZ,
    ADD COLUMN marketing_whatsapp_consent_at TIMESTAMPTZ;

-- Last touch: the visit that led to this booking.
ALTER TABLE bookings
    ADD COLUMN utm_source    TEXT CHECK (char_length(utm_source) BETWEEN 1 AND 200),
    ADD COLUMN utm_medium    TEXT CHECK (char_length(utm_medium) BETWEEN 1 AND 200),
    ADD COLUMN utm_campaign  TEXT CHECK (char_length(utm_campaign) BETWEEN 1 AND 200),
    ADD COLUMN utm_term      TEXT CHECK (char_length(utm_term) BETWEEN 1 AND 200),
    ADD COLUMN utm_content   TEXT CHECK (char_length(utm_content) BETWEEN 1 AND 200),
    ADD COLUMN gclid         TEXT CHECK (char_length(gclid) BETWEEN 1 AND 500),
    ADD COLUMN fbclid        TEXT CHECK (char_length(fbclid) BETWEEN 1 AND 500),
    ADD COLUMN referrer      TEXT CHECK (char_length(referrer) BETWEEN 1 AND 1000),
    ADD COLUMN landing_path  TEXT CHECK (char_length(landing_path) BETWEEN 1 AND 500),
    ADD COLUMN touch_seen_at TIMESTAMPTZ,
    ADD COLUMN device_type   TEXT CHECK (device_type IN ('MOBILE', 'TABLET', 'DESKTOP'));

-- The admin Insights dashboard (§7.15) filters on these.
CREATE INDEX users_created_at_idx ON users (created_at);
CREATE INDEX bookings_confirmed_at_idx ON bookings (confirmed_at) WHERE confirmed_at IS NOT NULL;
