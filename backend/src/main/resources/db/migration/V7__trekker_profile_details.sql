-- Trekker profile: fitness, diet, allergies, altitude history and rental shoe size. See docs/TRD.md §6.7.

ALTER TABLE trekker_profiles
    ADD COLUMN height_cm          INT CHECK (height_cm BETWEEN 100 AND 250),
    ADD COLUMN weight_kg          INT CHECK (weight_kg BETWEEN 25 AND 250),
    ADD COLUMN diet               TEXT CHECK (diet IN ('VEGETARIAN', 'EGGETARIAN', 'NON_VEGETARIAN', 'VEGAN', 'JAIN')),
    ADD COLUMN allergies          TEXT,
    ADD COLUMN highest_altitude_m INT CHECK (highest_altitude_m BETWEEN 0 AND 8849),
    ADD COLUMN shoe_size_uk       INT CHECK (shoe_size_uk BETWEEN 1 AND 15);
