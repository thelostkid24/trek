package com.sahyatri.guide.dto;

import com.sahyatri.guide.entity.GuideRouteKind;
import com.sahyatri.guide.entity.RouteAvailability;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.UUID;

/**
 * One route in the admin's record form (§7.21.8). {@code id} keeps an existing row (and the stats that point at it);
 * null adds one. Either {@code trackId} or {@code name}. Null kind is TREK, null availability is ASK; blank text clears.
 */
public record RouteRequest(
        UUID id,
        UUID trackId,
        @Size(max = 80) String name,
        GuideRouteKind kind,
        @Min(1) @Max(1000) Integer timesLed,
        @Min(1950) @Max(2100) Integer lastLedYear,
        @Min(1) @Max(12) Integer lastLedMonth,
        Boolean mostRecent,
        RouteAvailability availability,
        @Min(1) @Max(12) Integer seasonFromMonth,
        @Min(1) @Max(12) Integer seasonToMonth,
        LocalDate availableFrom,
        @Size(max = 200) String note) {
}
