package com.sahyatri.insights.dto;

/**
 * One source or campaign. {@code accounts} counts new accounts by first touch; {@code confirmedBookings} and
 * {@code grossPaise} count bookings confirmed in the window by their own last touch.
 */
public record SourceRow(String source, long accounts, long confirmedBookings, long grossPaise) {
}
