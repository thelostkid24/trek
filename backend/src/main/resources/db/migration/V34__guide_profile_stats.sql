-- Which numbers a guide's page shows, picked by an admin (docs/TRD.md §6.27, §7.21.8). Only the choice is stored;
-- every value is worked out on each read (law 8). treks_last_3y is the count an admin recorded from onboarding of
-- treks led elsewhere in the last three years; completed departures with us are added on read.

ALTER TABLE guide_profiles ADD COLUMN treks_last_3y INT CHECK (treks_last_3y BETWEEN 0 AND 1000);

CREATE TABLE guide_profile_stats (
    guide_id UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    position INT  NOT NULL CHECK (position BETWEEN 0 AND 6),
    stat_key TEXT NOT NULL CHECK (stat_key IN ('RATING', 'TREKS_LED', 'TREKS_LAST_3Y', 'YEARS_LEADING', 'ROUTES_LED',
                                               'PEAK_CLIMBS', 'EVACUATIONS', 'COURSES', 'TOP_COURSE', 'ROUTE_TIMES',
                                               'HIGHEST_PASS', 'RETURN_RATE')),
    route_id UUID REFERENCES guide_routes (id) ON DELETE CASCADE,
    PRIMARY KEY (guide_id, position),
    CHECK ((route_id IS NOT NULL) = (stat_key = 'ROUTE_TIMES'))
);
