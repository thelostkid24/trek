package com.sahyatri.catalog.dto;

import com.sahyatri.catalog.entity.Departure;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.UUID;

/** Create or edit a draft departure. The start-date rule is checked in the service (IST calendar). */
public record DepartureRequest(
        @NotNull UUID trackId,
        @NotNull UUID guideId,
        @NotNull LocalDate startDate,
        @NotNull @Min(10_000) @Max(10_000_000) Long pricePaise,
        // Law 2: small batch.
        @NotNull @Min(1) @Max(Departure.MAX_GROUP_SIZE) Integer maxGroupSize) {
}
