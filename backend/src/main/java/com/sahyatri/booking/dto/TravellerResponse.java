package com.sahyatri.booking.dto;

import com.sahyatri.booking.entity.BookingTraveller;
import com.sahyatri.booking.entity.TravellerAddons;
import com.sahyatri.profile.entity.Gender;

import java.time.LocalDate;

public record TravellerResponse(String fullName, String phone, LocalDate dateOfBirth, Gender gender,
                                boolean insurance, String insuranceId, boolean offloading, boolean transport) {

    public static TravellerResponse of(BookingTraveller t) {
        TravellerAddons a = t.getAddons();
        return new TravellerResponse(t.getFullName(), t.getPhone(), t.getDateOfBirth(), t.getGender(),
                a.insurance(), a.ownInsuranceId(), a.offloading(), a.transport());
    }
}
