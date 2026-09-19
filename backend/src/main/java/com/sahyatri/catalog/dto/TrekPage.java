package com.sahyatri.catalog.dto;

import java.util.List;

/** Public trek page: the route and its upcoming departures, soonest first. */
public record TrekPage(TrackDetail track, List<TrekDeparture> departures) {
}
