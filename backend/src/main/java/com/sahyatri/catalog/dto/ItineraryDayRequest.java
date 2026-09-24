package com.sahyatri.catalog.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/** One day of the itinerary. Only the heading is required; altitudes are metres. */
public record ItineraryDayRequest(
        @NotBlank @Size(max = 200) String summary,
        @Size(max = 1000) String description,
        @DecimalMin("0.1") @DecimalMax("99.9") @Digits(integer = 2, fraction = 1) BigDecimal distanceKm,
        @Min(1) @Max(9000) Integer startAltitudeM,
        @Min(1) @Max(9000) Integer highAltitudeM,
        @Min(1) @Max(9000) Integer endAltitudeM,
        @DecimalMin("0.5") @DecimalMax("24") @Digits(integer = 2, fraction = 1) BigDecimal hoursMin,
        @DecimalMin("0.5") @DecimalMax("24") @Digits(integer = 2, fraction = 1) BigDecimal hoursMax,
        @Size(max = 60) String routeNote) {
}
