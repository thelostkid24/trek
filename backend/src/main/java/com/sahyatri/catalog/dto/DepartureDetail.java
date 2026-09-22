package com.sahyatri.catalog.dto;

import com.sahyatri.catalog.entity.DepartureStatus;

import java.time.LocalDate;
import java.util.UUID;

/** Public departure page. The guide card carries their home and how often they've led this trek. */
public record DepartureDetail(
        UUID id,
        TrackDetail track,
        GuideCard guide,
        LocalDate startDate,
        LocalDate endDate,
        long pricePaise,
        int maxGroupSize,
        int seatsLeft,
        boolean bookable,
        DepartureStatus status) {
}
