package com.sahyatri.catalog.dto;

import com.sahyatri.catalog.entity.Difficulty;
import com.sahyatri.catalog.entity.Track;
import com.sahyatri.catalog.entity.TrackItineraryDay;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record TrackResponse(
        UUID id,
        String slug,
        String name,
        String region,
        Difficulty difficulty,
        int durationDays,
        Integer maxAltitudeM,
        String summary,
        String description,
        String meetingPoint,
        BigDecimal distanceKm,
        Integer baseAltitudeM,
        Integer highestCampM,
        String stay,
        String seasonLabel,
        List<String> itinerary,
        Instant createdAt,
        Instant updatedAt) {

    public static TrackResponse of(Track t) {
        return new TrackResponse(t.getId(), t.getSlug(), t.getName(), t.getRegion(), t.getDifficulty(),
                t.getDurationDays(), t.getMaxAltitudeM(), t.getSummary(), t.getDescription(), t.getMeetingPoint(),
                t.getDistanceKm(), t.getBaseAltitudeM(), t.getHighestCampM(), t.getStay(), t.getSeasonLabel(),
                t.getItinerary().stream().map(TrackItineraryDay::getSummary).toList(),
                t.getCreatedAt(), t.getUpdatedAt());
    }
}
