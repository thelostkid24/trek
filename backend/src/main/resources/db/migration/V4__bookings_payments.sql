-- Bookings, travellers, Razorpay payments, refunds, webhook dedupe. See docs/TRD.md §6.4.

CREATE TABLE bookings (
    id                   UUID PRIMARY KEY,
    user_id              UUID        NOT NULL REFERENCES users (id),
    departure_id         UUID        NOT NULL REFERENCES departures (id),
    seats                INT         NOT NULL CHECK (seats BETWEEN 1 AND 6),
    price_paise_per_seat BIGINT      NOT NULL CHECK (price_paise_per_seat > 0),
    amount_paise         BIGINT      NOT NULL,
    status               TEXT        NOT NULL CHECK (status IN ('HELD', 'CONFIRMED', 'EXPIRED', 'RELEASED',
                                                                 'CANCELLED_BY_TREKKER', 'CANCELLED_FORCE_MAJEURE')),
    hold_expires_at      TIMESTAMPTZ NOT NULL,
    confirmed_at         TIMESTAMPTZ,
    refund_policy        JSONB,
    cancelled_at         TIMESTAMPTZ,
    created_at           TIMESTAMPTZ NOT NULL,
    updated_at           TIMESTAMPTZ NOT NULL,
    CONSTRAINT bookings_amount CHECK (amount_paise = price_paise_per_seat * seats),
    CONSTRAINT bookings_confirmed CHECK (
        (status IN ('CONFIRMED', 'CANCELLED_BY_TREKKER', 'CANCELLED_FORCE_MAJEURE'))
            = (confirmed_at IS NOT NULL AND refund_policy IS NOT NULL)),
    CONSTRAINT bookings_cancelled CHECK (
        (status IN ('CANCELLED_BY_TREKKER', 'CANCELLED_FORCE_MAJEURE')) = (cancelled_at IS NOT NULL))
);

-- One live booking per trekker per departure.
CREATE UNIQUE INDEX bookings_one_live_per_trekker_idx ON bookings (departure_id, user_id)
    WHERE status IN ('HELD', 'CONFIRMED');
CREATE INDEX bookings_status_hold_expires_at_idx ON bookings (status, hold_expires_at);
CREATE INDEX bookings_user_id_created_at_idx ON bookings (user_id, created_at);
CREATE INDEX bookings_departure_id_status_idx ON bookings (departure_id, status);

CREATE TABLE booking_travellers (
    id            UUID PRIMARY KEY,
    booking_id    UUID NOT NULL REFERENCES bookings (id) ON DELETE CASCADE,
    position      INT  NOT NULL CHECK (position BETWEEN 0 AND 5),
    full_name     TEXT NOT NULL,
    phone         TEXT,
    date_of_birth DATE NOT NULL,
    gender        TEXT NOT NULL CHECK (gender IN ('FEMALE', 'MALE', 'NON_BINARY', 'PREFER_NOT_TO_SAY')),
    UNIQUE (booking_id, position)
);

CREATE TABLE payments (
    id                    UUID PRIMARY KEY,
    user_id               UUID        NOT NULL REFERENCES users (id),
    booking_id            UUID        NOT NULL REFERENCES bookings (id),
    razorpay_order_id     TEXT        NOT NULL UNIQUE,
    razorpay_payment_id   TEXT UNIQUE,
    amount_paise          BIGINT      NOT NULL CHECK (amount_paise > 0),
    amount_refunded_paise BIGINT      NOT NULL DEFAULT 0,
    currency              TEXT        NOT NULL DEFAULT 'INR',
    status                TEXT        NOT NULL CHECK (status IN ('CREATED', 'PAID', 'FAILED', 'EXPIRED')),
    method                TEXT CHECK (method IN ('UPI', 'CARD', 'NETBANKING', 'WALLET')),
    method_detail         JSONB,
    failure_code          TEXT,
    failure_reason        TEXT,
    paid_at               TIMESTAMPTZ,
    created_at            TIMESTAMPTZ NOT NULL,
    updated_at            TIMESTAMPTZ NOT NULL,
    CONSTRAINT payments_refund_cap CHECK (amount_refunded_paise BETWEEN 0 AND amount_paise),
    CONSTRAINT payments_paid CHECK ((status = 'PAID') = (paid_at IS NOT NULL AND razorpay_payment_id IS NOT NULL))
);

CREATE INDEX payments_booking_id_idx ON payments (booking_id);
CREATE INDEX payments_status_created_at_idx ON payments (status, created_at);

CREATE TABLE payment_refunds (
    id                 UUID PRIMARY KEY,
    payment_id         UUID        NOT NULL REFERENCES payments (id),
    razorpay_refund_id TEXT UNIQUE,
    amount_paise       BIGINT      NOT NULL CHECK (amount_paise > 0),
    status             TEXT        NOT NULL CHECK (status IN ('PENDING', 'PROCESSED', 'FAILED')),
    kind               TEXT        NOT NULL CHECK (kind IN ('TREKKER_CANCELLATION', 'FORCE_MAJEURE', 'LATE_CAPTURE')),
    reason             TEXT        NOT NULL,
    created_at         TIMESTAMPTZ NOT NULL,
    updated_at         TIMESTAMPTZ NOT NULL
);

CREATE INDEX payment_refunds_payment_id_idx ON payment_refunds (payment_id);
CREATE INDEX payment_refunds_status_created_at_idx ON payment_refunds (status, created_at);

CREATE TABLE webhook_events (
    event_id     TEXT PRIMARY KEY,
    event_type   TEXT        NOT NULL,
    received_at  TIMESTAMPTZ NOT NULL,
    processed_at TIMESTAMPTZ
);
