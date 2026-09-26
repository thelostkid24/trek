package com.sahyatri.insights.dto;

import java.util.UUID;

/** Bookings confirmed in the window per trek; rating and reviews are all-time. */
public record TrekRow(UUID trackId, String name, String slug, long confirmedBookings, long seats, long grossPaise,
                      Double avgRating, long reviews) {
}
