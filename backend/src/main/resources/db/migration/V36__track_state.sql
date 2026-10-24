-- The Indian state a trek is in, so /guides can filter guides by the states of the treks they lead (docs/TRD.md §6,
-- §7.17). Null until an admin picks one; the admin form offers the states and union territories.

ALTER TABLE tracks ADD COLUMN state TEXT CHECK (char_length(state) BETWEEN 1 AND 60);
