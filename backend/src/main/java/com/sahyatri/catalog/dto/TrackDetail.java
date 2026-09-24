package com.sahyatri.catalog.dto;

import com.sahyatri.catalog.entity.Difficulty;
import com.sahyatri.catalog.entity.Track;
import com.sahyatri.common.storage.TrackPhotoFiles;

import java.math.BigDecimal;
import java.util.List;

/** Track fields shown on the public trek and departure pages. Facts may be null until an admin adds them. */
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
        String pickupDrop,
        Boolean cloakroom,
        Boolean offloading,
        Long offloadingPricePaise,
        List<ItineraryDay> itinerary,
        List<TrackPhotoResponse> photos) {

    public static TrackDetail of(Track t, TrackPhotoFiles photoFiles) {
        return new TrackDetail(t.getSlug(), t.getName(), t.getRegion(), t.getDifficulty(), t.getDurationDays(),
                t.getSummary(), t.getDescription(), t.getMaxAltitudeM(), t.getMeetingPoint(), t.getDistanceKm(),
                t.getBaseAltitudeM(), t.getHighestCampM(), t.getStay(), t.getSeasonLabel(), t.getPickupDrop(),
                t.getCloakroom(), t.getOffloading(), t.getOffloadingPricePaise(),
                t.getItinerary().stream().map(ItineraryDay::of).toList(),
                t.getPhotos().stream().map(p -> TrackPhotoResponse.of(p, photoFiles)).toList());
    }
}
