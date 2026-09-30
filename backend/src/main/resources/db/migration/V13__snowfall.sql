-- Has snow fallen on the trek this week? The question trekkers search for most. Null when not reported.
-- See docs/TRD.md §6 and §7.13.
ALTER TABLE snow_reports
    ADD COLUMN snowfall TEXT CHECK (snowfall IN ('NONE', 'LIGHT', 'HEAVY'));
