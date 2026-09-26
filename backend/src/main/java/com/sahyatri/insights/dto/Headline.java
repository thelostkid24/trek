package com.sahyatri.insights.dto;

/**
 * @param holdToPaidBps share of settled holds made in the window that were paid; null with none settled
 * @param avgGroupSize  average seats per booking confirmed in the window; null with none
 */
public record Headline(
        long newAccounts,
        long bookingsHeld,
        long bookingsConfirmed,
        long grossPaise,
        Integer holdToPaidBps,
        Double avgGroupSize,
        long cancellations) {
}
