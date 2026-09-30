-- Add-ons are chosen per traveller in "Who's coming?", after the hold and before paying. Insurance is compulsory
-- where the trek offers it: each traveller takes ours or gives their own policy ID. The booking's add-on counts
-- (V14) are worked out from these rows. See docs/TRD.md §6 and §7.6.
ALTER TABLE booking_travellers
    ADD COLUMN takes_insurance  BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN own_insurance_id TEXT,
    ADD COLUMN takes_offloading BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN takes_transport  BOOLEAN NOT NULL DEFAULT FALSE,
    ADD CONSTRAINT booking_travellers_one_insurance CHECK (NOT (takes_insurance AND own_insurance_id IS NOT NULL));
