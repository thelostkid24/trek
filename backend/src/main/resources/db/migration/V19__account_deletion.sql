-- A trekker can delete their own account. The row stays, because bookings, payments, reviews and audit events point
-- at it and must be kept, but everything that identifies the person is erased. See docs/TRD.md §6.15 and §7.18.

ALTER TABLE users DROP CONSTRAINT users_status_check;
ALTER TABLE users ADD CONSTRAINT users_status_check CHECK (status IN ('ACTIVE', 'DISABLED', 'DELETED'));

ALTER TABLE users ADD COLUMN deleted_at TIMESTAMPTZ;
ALTER TABLE users ADD CONSTRAINT users_deleted CHECK ((status = 'DELETED') = (deleted_at IS NOT NULL));
