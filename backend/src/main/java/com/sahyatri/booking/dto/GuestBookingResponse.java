package com.sahyatri.booking.dto;

import com.sahyatri.auth.dto.AuthResponse;

/** The held booking plus the guest's session; the refresh token travels in the cookie as usual. */
public record GuestBookingResponse(BookingResponse booking, AuthResponse auth) {
}
