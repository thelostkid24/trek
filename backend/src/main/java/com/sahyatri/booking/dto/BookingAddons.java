package com.sahyatri.booking.dto;

import com.sahyatri.booking.entity.Booking;

/** The add-ons on a booking: seats taking each, the price per seat it was held at, and their total. */
public record BookingAddons(
        int insuranceSeats,
        Long insurancePricePaise,
        int offloadingSeats,
        Long offloadingPricePaise,
        int transportSeats,
        Long transportPricePaise,
        long totalPaise) {

    public static BookingAddons of(Booking b) {
        return new BookingAddons(b.getInsuranceSeats(), b.getInsurancePricePaise(), b.getOffloadingSeats(),
                b.getOffloadingPricePaise(), b.getTransportSeats(), b.getTransportPricePaise(), b.getAddonsPaise());
    }
}
