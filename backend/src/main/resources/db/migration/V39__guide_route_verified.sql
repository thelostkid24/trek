-- An admin can mark a guide "Route verified" on the guides list (docs/TRD.md §6.30, §7.17) without the 7+ treks led
-- that earn it otherwise.
ALTER TABLE guide_profiles ADD COLUMN route_verified BOOLEAN NOT NULL DEFAULT false;
