package com.sahyatri.catalog.dto;

import java.util.List;
import java.util.UUID;

/**
 * Public guide page. Counts are completed departures, computed at read time and never stored (law 8).
 */
public record GuideProfile(
        UUID id,
        String fullName,
        String avatarUrl,
        String homeCity,
        String bio,
        long treksLed,
        List<TrekLed> treks,
        List<DepartureSummary> upcoming) {

    public record TrekLed(TrackBrief track, long times) {
    }
}
