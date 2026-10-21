package com.sahyatri.guide.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/** A range of days a guide is free, both ends included. A blank note is none. */
public record AvailabilityRequest(@NotNull LocalDate startDate, @NotNull LocalDate endDate, @Size(max = 120) String note) {
}
