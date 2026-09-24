package com.sahyatri.catalog.dto;

import com.sahyatri.catalog.entity.TrackItineraryDay;

import java.math.BigDecimal;

/** One day of a track's itinerary, as the admin form and the trek page read it. Details may be null. */
public record ItineraryDay(
        int day,
        String summary,
        String description,
        BigDecimal distanceKm,
        Integer startAltitudeM,
        Integer highAltitudeM,
        Integer endAltitudeM,
        BigDecimal hoursMin,
        BigDecimal hoursMax,
        String routeNote) {

    public static ItineraryDay of(TrackItineraryDay d) {
        return new ItineraryDay(d.getDayNumber(), d.getSummary(), d.getDescription(), d.getDistanceKm(),
                d.getStartAltitudeM(), d.getHighAltitudeM(), d.getEndAltitudeM(), d.getHoursMin(), d.getHoursMax(),
                d.getRouteNote());
    }
}
