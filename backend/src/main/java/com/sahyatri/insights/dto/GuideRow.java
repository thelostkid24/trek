package com.sahyatri.insights.dto;

import java.util.UUID;

/** All-time, computed at read time (law 8). {@code avgFillBps} is over completed departures. */
public record GuideRow(UUID guideId, String name, long departuresCompleted, Integer avgFillBps, Double avgRating,
                       long reviews) {
}
