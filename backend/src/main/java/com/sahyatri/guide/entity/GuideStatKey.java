package com.sahyatri.guide.entity;

/**
 * A number an admin can pick for a guide's stats row (§7.21.8). Values are worked out on each read (law 8), never
 * stored.
 */
public enum GuideStatKey {
    /** The rating and review count, or "New · no reviews yet". */
    RATING,
    /** Treks led, with us and before (the sum of the routes). */
    TREKS_LED,
    /** Treks led in the last three years: the admin's onboarding count plus completed departures with us since. */
    TREKS_LAST_3Y,
    YEARS_LEADING,
    ROUTES_LED,
    /** Routes of kind PEAK, named in the label. */
    PEAK_CLIMBS,
    /** "0 evacuations on treks led", shown only with the NO_SERIOUS_INCIDENTS pledge. */
    EVACUATIONS,
    /** BMC and AMC courses on record, with the institutes. */
    COURSES,
    /** The highest mountaineering course on record (AMC, else BMC). */
    TOP_COURSE,
    /** How often they've led one route; needs the route. */
    ROUTE_TIMES,
    HIGHEST_PASS,
    RETURN_RATE,
    /** Typed in by an admin: its own value and label, stored as typed (it doesn't update itself). */
    CUSTOM
}
