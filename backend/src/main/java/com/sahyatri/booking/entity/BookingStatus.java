package com.sahyatri.booking.entity;

public enum BookingStatus {
    HELD, CONFIRMED, EXPIRED, RELEASED, CANCELLED_BY_TREKKER, CANCELLED_FORCE_MAJEURE;

    /** Counts toward the departure's seats and blocks a second booking by the same trekker. */
    public boolean isLive() {
        return this == HELD || this == CONFIRMED;
    }
}
