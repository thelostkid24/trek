-- The "Batches led" number an admin types in for a guide (docs/TRD.md §6.31, §7.17). Null = worked out from their
-- routes and completed departures, as before.
ALTER TABLE guide_profiles ADD COLUMN batches_led INT CHECK (batches_led BETWEEN 0 AND 10000);
