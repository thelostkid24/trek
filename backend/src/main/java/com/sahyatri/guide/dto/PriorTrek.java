package com.sahyatri.guide.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/** A trek and how often the guide led it before Sahyātri. */
public record PriorTrek(@NotNull UUID trackId, @Min(1) @Max(1000) int times) {
}
