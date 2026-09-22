package com.sahyatri.catalog.dto;

import java.time.LocalDate;
import java.util.UUID;

/** A departure as listed under its trek in the catalog. */
public record CatalogDeparture(
        UUID id,
        LocalDate startDate,
        LocalDate endDate,
        long pricePaise,
        int maxGroupSize,
        int seatsLeft,
        boolean bookable,
        GuideBrief guide) {
}
