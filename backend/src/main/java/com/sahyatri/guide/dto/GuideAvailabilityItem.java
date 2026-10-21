package com.sahyatri.guide.dto;

import java.time.LocalDate;
import java.util.UUID;

/** A guide's free range as admins see it next to the departure and planned-date pickers. */
public record GuideAvailabilityItem(UUID id, UUID guideId, String guideName, String slug, LocalDate startDate,
                                    LocalDate endDate, String note) {
}
