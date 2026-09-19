package com.sahyatri.booking.dto;

import com.sahyatri.booking.entity.BookingTraveller;
import com.sahyatri.profile.entity.Gender;

import java.time.LocalDate;

public record TravellerResponse(String fullName, String phone, LocalDate dateOfBirth, Gender gender) {

    public static TravellerResponse of(BookingTraveller t) {
        return new TravellerResponse(t.getFullName(), t.getPhone(), t.getDateOfBirth(), t.getGender());
    }
}
