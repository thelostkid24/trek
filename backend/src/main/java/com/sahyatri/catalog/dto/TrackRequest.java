package com.sahyatri.catalog.dto;

import com.sahyatri.catalog.entity.Difficulty;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

/**
 * Create or replace a track. Text is trimmed in the service. Route facts and the itinerary are optional; an
 * itinerary, when given, has one line per day of {@code durationDays} (checked in the service).
 */
public record TrackRequest(
        @NotBlank @Size(min = 3, max = 80)
        @Pattern(regexp = "^[a-z0-9]+(-[a-z0-9]+)*$", message = "must be lowercase letters and digits separated by hyphens")
        String slug,
        @NotBlank @Size(max = 100) String name,
        @NotBlank @Size(max = 100) String region,
        @NotNull Difficulty difficulty,
        @NotNull @Min(1) @Max(7) Integer durationDays,
        @Min(1) @Max(9000) Integer maxAltitudeM,
        @NotBlank @Size(max = 200) String summary,
        @NotBlank @Size(max = 5000) String description,
        @NotBlank @Size(max = 300) String meetingPoint,
        @DecimalMin("0.1") @DecimalMax("999.9") @Digits(integer = 3, fraction = 1) BigDecimal distanceKm,
        @Min(1) @Max(9000) Integer baseAltitudeM,
        @Min(1) @Max(9000) Integer highestCampM,
        @Size(max = 120) String stay,
        @Size(max = 60) String seasonLabel,
        @Size(max = 7) List<@NotBlank @Size(max = 200) String> itinerary) {
}
