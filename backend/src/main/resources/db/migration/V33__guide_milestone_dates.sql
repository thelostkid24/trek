-- Career milestones with looser dates (docs/TRD.md §6.19, §7.21.1): "~2019", "Nov 2026", "17 Nov 2026", or none ("—").

ALTER TABLE guide_milestones
    ALTER COLUMN year DROP NOT NULL,
    ADD COLUMN month       INT CHECK (month BETWEEN 1 AND 12),
    ADD COLUMN day         INT CHECK (day BETWEEN 1 AND 31),
    ADD COLUMN approximate BOOLEAN NOT NULL DEFAULT false,
    ADD CONSTRAINT guide_milestones_month_needs_year CHECK (month IS NULL OR year IS NOT NULL),
    ADD CONSTRAINT guide_milestones_day_needs_month CHECK (day IS NULL OR month IS NOT NULL),
    ADD CONSTRAINT guide_milestones_approximate_year CHECK (NOT approximate OR (year IS NOT NULL AND month IS NULL));
