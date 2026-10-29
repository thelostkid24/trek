-- An admin marks a departure the founder walks with the guide; the trek page shows "Founder co-leads the batch"
-- on its date (docs/TRD.md §6, §7.20). Not a booking term: switching it never touches bookings or money.
ALTER TABLE departures ADD COLUMN founder_co_leads BOOLEAN NOT NULL DEFAULT false;
