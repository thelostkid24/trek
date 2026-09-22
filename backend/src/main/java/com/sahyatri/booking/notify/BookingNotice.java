package com.sahyatri.booking.notify;

import java.util.UUID;

/** Published inside the transaction that changed the booking; emailed only after it commits. */
public record BookingNotice(UUID bookingId, Kind kind, long refundPaise) {

    public enum Kind { CONFIRMED, CANCELLED_BY_TREKKER, CANCELLED_FORCE_MAJEURE }
}
