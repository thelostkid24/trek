package com.sahyatri.booking.dto;

public record CancellationQuote(boolean allowed, long daysBeforeStart, int refundBps, long refundPaise) {
}
