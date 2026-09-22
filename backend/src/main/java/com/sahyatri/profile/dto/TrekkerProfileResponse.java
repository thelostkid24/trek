package com.sahyatri.profile.dto;

import com.sahyatri.profile.entity.Diet;
import com.sahyatri.profile.entity.ExperienceLevel;
import com.sahyatri.profile.entity.Gender;

import java.time.Instant;
import java.time.LocalDate;

public record TrekkerProfileResponse(
        String fullName,
        String avatarUrl,
        LocalDate dateOfBirth,
        Gender gender,
        String homeCity,
        ExperienceLevel experienceLevel,
        Integer highestAltitudeM,
        String bio,
        EmergencyContact emergencyContact,
        Integer heightCm,
        Integer weightKg,
        String bloodGroup,
        String allergies,
        String medicalNotes,
        Diet diet,
        Integer shoeSizeUk,
        ProfileCompletion completion,
        Instant updatedAt) {
}
