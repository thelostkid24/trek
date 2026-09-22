package com.sahyatri.booking.dto;

/** Also the JSON stored in {@code bookings.refund_policy}. */
public record RefundTierResponse(int minDaysBefore, int refundBps) {
}
