package com.sahyatri.catalog.dto;

import java.time.LocalDate;
import java.util.UUID;

/** One dated run on the trek page. Each can have its own guide. */
public record TrekDeparture(
        UUID id,
        LocalDate startDate,
        LocalDate endDate,
        long pricePaise,
        int maxGroupSize,
        int seatsLeft,
        boolean bookable,
        GuideCard guide) {
}
