package com.sahyatri.guide.entity;

/**
 * When a guide can lead a route (§7.21.8): any time (optionally within a season of months), from a date, not open
 * yet, or "Ask <name>".
 */
public enum RouteAvailability {
    ANY_TIME,
    FROM_DATE,
    NOT_OPEN,
    ASK
}
