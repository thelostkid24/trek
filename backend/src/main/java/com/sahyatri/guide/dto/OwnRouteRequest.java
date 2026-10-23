package com.sahyatri.guide.dto;

import com.sahyatri.guide.entity.RouteAvailability;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.UUID;

/**
 * The guide's part of one of their routes (§7.21.8): when they can lead it and their note. {@code id} names one of
 * their rows; or, with no id, {@code trackId} names a trek they've led or lead with us, which adds a row. Null
 * availability is ASK; a blank note clears it.
 */
public record OwnRouteRequest(
        UUID id,
        UUID trackId,
        RouteAvailability availability,
        @Min(1) @Max(12) Integer seasonFromMonth,
        @Min(1) @Max(12) Integer seasonToMonth,
        LocalDate availableFrom,
        @Size(max = 200) String note) {
}
