-- A checkout code buys one seat hold (docs/TRD.md §7.6): used_at is set in the hold's transaction, so a hold that
-- fails leaves the code usable and two holds can't share one.

ALTER TABLE booking_email_codes ADD COLUMN used_at TIMESTAMPTZ;
