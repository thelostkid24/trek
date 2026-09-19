package com.sahyatri.profile.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Trekker-only details, 1:1 with users. Name, email, phone and photo stay on User. */
@Entity
@Table(name = "trekker_profiles")
public class TrekkerProfile {

    @Id
    private UUID userId;

    private LocalDate dateOfBirth;

    @Enumerated(EnumType.STRING)
    private Gender gender;

    private String homeCity;

    @Enumerated(EnumType.STRING)
    private ExperienceLevel experienceLevel;

    private String bio;

    private String emergencyName;

    private String emergencyRelation;

    private String emergencyPhone;

    /** One of A+ A- B+ B- AB+ AB- O+ O- (DB CHECK). */
    private String bloodGroup;

    private String medicalNotes;

    private Integer heightCm;

    private Integer weightKg;

    @Enumerated(EnumType.STRING)
    private Diet diet;

    private String allergies;

    @Column(name = "highest_altitude_m")
    private Integer highestAltitudeM;

    private Integer shoeSizeUk;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    protected TrekkerProfile() {
    }

    public static TrekkerProfile newFor(UUID userId) {
        TrekkerProfile profile = new TrekkerProfile();
        profile.userId = userId;
        profile.createdAt = Instant.now();
        profile.updatedAt = profile.createdAt;
        return profile;
    }

    @PreUpdate
    void touch() {
        updatedAt = Instant.now();
    }

    public boolean hasEmergencyContact() {
        return emergencyPhone != null;
    }

    public void setEmergencyContact(String name, String relation, String phone) {
        this.emergencyName = name;
        this.emergencyRelation = relation;
        this.emergencyPhone = phone;
    }

    public void clearEmergencyContact() {
        setEmergencyContact(null, null, null);
    }

    public UUID getUserId() {
        return userId;
    }

    public LocalDate getDateOfBirth() {
        return dateOfBirth;
    }

    public void setDateOfBirth(LocalDate dateOfBirth) {
        this.dateOfBirth = dateOfBirth;
    }

    public Gender getGender() {
        return gender;
    }

    public void setGender(Gender gender) {
        this.gender = gender;
    }

    public String getHomeCity() {
        return homeCity;
    }

    public void setHomeCity(String homeCity) {
        this.homeCity = homeCity;
    }

    public ExperienceLevel getExperienceLevel() {
        return experienceLevel;
    }

    public void setExperienceLevel(ExperienceLevel experienceLevel) {
        this.experienceLevel = experienceLevel;
    }

    public String getBio() {
        return bio;
    }

    public void setBio(String bio) {
        this.bio = bio;
    }

    public String getEmergencyName() {
        return emergencyName;
    }

    public String getEmergencyRelation() {
        return emergencyRelation;
    }

    public String getEmergencyPhone() {
        return emergencyPhone;
    }

    public String getBloodGroup() {
        return bloodGroup;
    }

    public void setBloodGroup(String bloodGroup) {
        this.bloodGroup = bloodGroup;
    }

    public String getMedicalNotes() {
        return medicalNotes;
    }

    public void setMedicalNotes(String medicalNotes) {
        this.medicalNotes = medicalNotes;
    }

    public Integer getHeightCm() {
        return heightCm;
    }

    public void setHeightCm(Integer heightCm) {
        this.heightCm = heightCm;
    }

    public Integer getWeightKg() {
        return weightKg;
    }

    public void setWeightKg(Integer weightKg) {
        this.weightKg = weightKg;
    }

    public Diet getDiet() {
        return diet;
    }

    public void setDiet(Diet diet) {
        this.diet = diet;
    }

    public String getAllergies() {
        return allergies;
    }

    public void setAllergies(String allergies) {
        this.allergies = allergies;
    }

    public Integer getHighestAltitudeM() {
        return highestAltitudeM;
    }

    public void setHighestAltitudeM(Integer highestAltitudeM) {
        this.highestAltitudeM = highestAltitudeM;
    }

    public Integer getShoeSizeUk() {
        return shoeSizeUk;
    }

    public void setShoeSizeUk(Integer shoeSizeUk) {
        this.shoeSizeUk = shoeSizeUk;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
