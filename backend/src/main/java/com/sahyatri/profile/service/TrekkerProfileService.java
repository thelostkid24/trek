package com.sahyatri.profile.service;

import com.sahyatri.auth.entity.User;
import com.sahyatri.auth.service.CurrentUser;
import com.sahyatri.common.exception.ApiException;
import com.sahyatri.common.storage.AvatarFiles;
import com.sahyatri.profile.dto.EmergencyContact;
import com.sahyatri.profile.dto.ProfileCompletion;
import com.sahyatri.profile.dto.TrekkerProfileRequest;
import com.sahyatri.profile.dto.TrekkerProfileResponse;
import com.sahyatri.profile.entity.TrekkerProfile;
import com.sahyatri.profile.repository.TrekkerProfileRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** The caller's own trekker profile. The user id always comes from the access token, never the request. */
@Service
public class TrekkerProfileService {

    public static final int MIN_AGE = 18;
    public static final int MAX_AGE = 100;
    /** Birthdays are judged on the Indian calendar date. */
    private static final ZoneId ZONE = ZoneId.of("Asia/Kolkata");

    private final TrekkerProfileRepository profiles;
    private final CurrentUser currentUser;
    private final AvatarFiles avatars;

    public TrekkerProfileService(TrekkerProfileRepository profiles, CurrentUser currentUser, AvatarFiles avatars) {
        this.profiles = profiles;
        this.currentUser = currentUser;
        this.avatars = avatars;
    }

    @Transactional(readOnly = true)
    public TrekkerProfileResponse get(UUID userId) {
        User user = currentUser.require(userId);
        TrekkerProfile profile = profiles.findById(userId).orElse(null);
        return toResponse(user, profile);
    }

    @Transactional
    public TrekkerProfileResponse update(UUID userId, TrekkerProfileRequest req) {
        User user = currentUser.require(userId);
        checkAge(req.dateOfBirth());
        EmergencyContact contact = req.emergencyContact();
        if (contact != null && contact.phone().equals(user.getPhone())) {
            throw ApiException.validation("emergency_contact.phone", "must be someone else's number");
        }

        user.setFullName(req.fullName().trim());

        TrekkerProfile profile = profiles.findById(userId).orElseGet(() -> TrekkerProfile.newFor(userId));
        profile.setDateOfBirth(req.dateOfBirth());
        profile.setGender(req.gender());
        profile.setHomeCity(blankToNull(req.homeCity()));
        profile.setExperienceLevel(req.experienceLevel());
        profile.setHighestAltitudeM(req.highestAltitudeM());
        profile.setBio(blankToNull(req.bio()));
        if (contact == null) {
            profile.clearEmergencyContact();
        } else {
            profile.setEmergencyContact(contact.name().trim(), contact.relation().trim(), contact.phone());
        }
        profile.setHeightCm(req.heightCm());
        profile.setWeightKg(req.weightKg());
        profile.setBloodGroup(req.bloodGroup());
        profile.setAllergies(blankToNull(req.allergies()));
        profile.setMedicalNotes(blankToNull(req.medicalNotes()));
        profile.setDiet(req.diet());
        profile.setShoeSizeUk(req.shoeSizeUk());
        profile = profiles.saveAndFlush(profile);
        return toResponse(user, profile);
    }

    private void checkAge(LocalDate dateOfBirth) {
        if (dateOfBirth == null) {
            return;
        }
        LocalDate today = LocalDate.now(ZONE);
        if (dateOfBirth.isAfter(today.minusYears(MIN_AGE))) {
            throw ApiException.validation("date_of_birth", "you must be at least " + MIN_AGE);
        }
        if (!dateOfBirth.isAfter(today.minusYears(MAX_AGE + 1))) {
            throw ApiException.validation("date_of_birth", "must be a real date of birth");
        }
    }

    private TrekkerProfileResponse toResponse(User user, TrekkerProfile p) {
        EmergencyContact contact = p != null && p.hasEmergencyContact()
                ? new EmergencyContact(p.getEmergencyName(), p.getEmergencyRelation(), p.getEmergencyPhone())
                : null;
        return new TrekkerProfileResponse(
                user.getFullName(),
                avatars.url(user.getAvatarKey()),
                p == null ? null : p.getDateOfBirth(),
                p == null ? null : p.getGender(),
                p == null ? null : p.getHomeCity(),
                p == null ? null : p.getExperienceLevel(),
                p == null ? null : p.getHighestAltitudeM(),
                p == null ? null : p.getBio(),
                contact,
                p == null ? null : p.getHeightCm(),
                p == null ? null : p.getWeightKg(),
                p == null ? null : p.getBloodGroup(),
                p == null ? null : p.getAllergies(),
                p == null ? null : p.getMedicalNotes(),
                p == null ? null : p.getDiet(),
                p == null ? null : p.getShoeSizeUk(),
                completion(user, p),
                p == null ? null : p.getUpdatedAt());
    }

    /** Order and keys are part of the contract (docs/TRD.md §7.3). */
    static ProfileCompletion completion(User user, TrekkerProfile p) {
        Map<String, Boolean> items = new LinkedHashMap<>();
        items.put("full_name", user.getFullName() != null);
        items.put("avatar", user.getAvatarKey() != null);
        items.put("date_of_birth", p != null && p.getDateOfBirth() != null);
        items.put("gender", p != null && p.getGender() != null);
        items.put("home_city", p != null && p.getHomeCity() != null);
        items.put("experience_level", p != null && p.getExperienceLevel() != null);
        items.put("emergency_contact", p != null && p.hasEmergencyContact());
        items.put("blood_group", p != null && p.getBloodGroup() != null);
        items.put("height_weight", p != null && p.getHeightCm() != null && p.getWeightKg() != null);
        items.put("diet", p != null && p.getDiet() != null);
        items.put("phone_verified", user.getPhoneVerifiedAt() != null);
        items.put("email_verified", user.getEmailVerifiedAt() != null);

        List<String> missing = new ArrayList<>();
        items.forEach((key, done) -> {
            if (!done) missing.add(key);
        });
        int percent = (items.size() - missing.size()) * 100 / items.size();
        return new ProfileCompletion(percent, missing);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
