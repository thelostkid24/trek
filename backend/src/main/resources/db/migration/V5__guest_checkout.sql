-- Guest checkout, booking contact details, groups of up to 10. See docs/TRD.md §6.5.

-- A guest is a trekker with no sign-in identity yet (no email, phone or Google). Verifying a phone or email
-- later turns it into a normal account.
ALTER TABLE users DROP CONSTRAINT users_has_identity;

-- Law 2: a batch is at most 10 per guide.
ALTER TABLE departures DROP CONSTRAINT departures_max_group_size_check;
ALTER TABLE departures ADD CONSTRAINT departures_max_group_size_check CHECK (max_group_size BETWEEN 1 AND 10);

ALTER TABLE bookings DROP CONSTRAINT bookings_seats_check;
ALTER TABLE bookings ADD CONSTRAINT bookings_seats_check CHECK (seats BETWEEN 1 AND 10);

ALTER TABLE booking_travellers DROP CONSTRAINT booking_travellers_position_check;
ALTER TABLE booking_travellers ADD CONSTRAINT booking_travellers_position_check CHECK (position BETWEEN 0 AND 9);

-- Who we reach on WhatsApp and email about this booking. Always set for new bookings; older rows are
-- backfilled from the account and may stay partly empty.
ALTER TABLE bookings
    ADD COLUMN contact_name  TEXT,
    ADD COLUMN contact_phone TEXT,
    ADD COLUMN contact_email TEXT;

UPDATE bookings b
SET contact_name  = u.full_name,
    contact_phone = u.phone,
    contact_email = u.email
FROM users u
WHERE u.id = b.user_id;
