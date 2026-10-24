-- A stat an admin types in by hand (docs/TRD.md §6.27, §7.21.8): CUSTOM, with its own value ("21") and label
-- ("treks led in 3 years"). Unlike the other kinds it is stored as typed and doesn't update itself.

ALTER TABLE guide_profile_stats DROP CONSTRAINT guide_profile_stats_stat_key_check;
ALTER TABLE guide_profile_stats ADD CONSTRAINT guide_profile_stats_stat_key_check
    CHECK (stat_key IN ('RATING', 'TREKS_LED', 'TREKS_LAST_3Y', 'YEARS_LEADING', 'ROUTES_LED', 'PEAK_CLIMBS',
                        'EVACUATIONS', 'COURSES', 'TOP_COURSE', 'ROUTE_TIMES', 'HIGHEST_PASS', 'RETURN_RATE', 'CUSTOM'));

ALTER TABLE guide_profile_stats
    ADD COLUMN custom_value TEXT CHECK (char_length(custom_value) BETWEEN 1 AND 20),
    ADD COLUMN custom_label TEXT CHECK (char_length(custom_label) BETWEEN 1 AND 80),
    ADD CONSTRAINT guide_profile_stats_custom_check
        CHECK ((stat_key = 'CUSTOM') = (custom_value IS NOT NULL AND custom_label IS NOT NULL)
            AND (custom_value IS NULL) = (custom_label IS NULL));
