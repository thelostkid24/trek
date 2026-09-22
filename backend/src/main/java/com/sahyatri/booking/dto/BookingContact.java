package com.sahyatri.booking.dto;

/** Who we reach about the booking. {@code phone} is the WhatsApp number. */
public record BookingContact(String fullName, String phone, String email) {
}
