package com.sahyatri.guide.dto;

import com.sahyatri.guide.entity.GuideStatKey;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** One number in a guide's stats row. {@code routeIndex}: for ROUTE_TIMES, the route's place in the record's routes. */
public record StatPick(@NotNull GuideStatKey key, @Min(0) Integer routeIndex) {
}
