package com.sahyatri.insights.dto;

import java.time.LocalDate;
import java.util.UUID;

/** A published departure starting in the next 60 days. */
public record UpcomingDeparture(UUID departureId, String trackName, LocalDate startDate, int seatsTaken,
                                int maxGroupSize, String guideName) {
}
