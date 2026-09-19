package com.sahyatri.catalog.dto;

import java.time.LocalDate;
import java.util.UUID;

/** Public list item. */
public record DepartureSummary(
        UUID id,
        TrackBrief track,
        GuideBrief guide,
        LocalDate startDate,
        LocalDate endDate,
        long pricePaise,
        int maxGroupSize,
        int seatsLeft,
        boolean bookable) {
}
