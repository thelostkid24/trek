package com.sahyatri.guide.dto;

import com.sahyatri.guide.entity.GuideStatKey;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * One number in a guide's stats row. {@code routeIndex}: for ROUTE_TIMES, the route's place in the record's routes.
 * {@code value} and {@code label}: what a CUSTOM stat shows ("21", "treks led in 3 years"); null for other kinds.
 */
public record StatPick(@NotNull GuideStatKey key, @Min(0) Integer routeIndex, @Size(max = 20) String value,
                       @Size(max = 80) String label) {
}
