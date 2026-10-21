package com.sahyatri.guide.dto;

import java.time.LocalDate;
import java.util.UUID;

/** One of the guide's own ranges, as Guide → Availability shows it. */
public record AvailabilityRange(UUID id, LocalDate startDate, LocalDate endDate, String note) {
}
