package com.sahyatri.catalog.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** New per-seat price for a published departure. Same bounds as a draft's. */
public record DeparturePriceRequest(@NotNull @Min(10_000) @Max(10_000_000) Long pricePaise) {
}
