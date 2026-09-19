package com.sahyatri.catalog.dto;

import com.sahyatri.catalog.entity.Difficulty;
import com.sahyatri.catalog.entity.Track;
import com.sahyatri.catalog.entity.TrackItineraryDay;

import java.math.BigDecimal;
import java.util.List;

/** Track fields shown on the public trek and departure pages. Route facts may be null until an admin adds them. */
public record TrackDetail(
        String slug,
        String name,
        String region,
        Difficulty difficulty,
        int durationDays,
        String summary,
        String description,
        Integer maxAltitudeM,
        String meetingPoint,
        BigDecimal distanceKm,
        Integer baseAltitudeM,
        Integer highestCampM,
        String stay,
        String seasonLabel,
        List<ItineraryDay> itinerary) {

    public record ItineraryDay(int day, String summary) {
    }

    public static TrackDetail of(Track t) {
        return new TrackDetail(t.getSlug(), t.getName(), t.getRegion(), t.getDifficulty(), t.getDurationDays(),
                t.getSummary(), t.getDescription(), t.getMaxAltitudeM(), t.getMeetingPoint(), t.getDistanceKm(),
                t.getBaseAltitudeM(), t.getHighestCampM(), t.getStay(), t.getSeasonLabel(),
                t.getItinerary().stream().map(TrackDetail::day).toList());
    }

    private static ItineraryDay day(TrackItineraryDay d) {
        return new ItineraryDay(d.getDayNumber(), d.getSummary());
    }
}
