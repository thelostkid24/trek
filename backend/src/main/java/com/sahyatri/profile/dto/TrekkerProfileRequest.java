package com.sahyatri.profile.dto;

import com.sahyatri.profile.entity.Diet;
import com.sahyatri.profile.entity.ExperienceLevel;
import com.sahyatri.profile.entity.Gender;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/** Full replacement of the editable profile. Age range and own-phone checks happen in the service. */
public record TrekkerProfileRequest(
        @NotBlank @Size(max = 100) String fullName,
        LocalDate dateOfBirth,
        Gender gender,
        @Size(max = 100) String homeCity,
        ExperienceLevel experienceLevel,
        @Min(0) @Max(8849) Integer highestAltitudeM,
        @Size(max = 500) String bio,
        @Valid EmergencyContact emergencyContact,
        @Min(100) @Max(250) Integer heightCm,
        @Min(25) @Max(250) Integer weightKg,
        @Pattern(regexp = "^(A|B|AB|O)[+-]$", message = "must be one of A+ A- B+ B- AB+ AB- O+ O-") String bloodGroup,
        @Size(max = 300) String allergies,
        @Size(max = 1000) String medicalNotes,
        Diet diet,
        @Min(1) @Max(15) Integer shoeSizeUk) {
}
