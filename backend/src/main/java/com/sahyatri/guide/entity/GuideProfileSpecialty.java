package com.sahyatri.guide.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import java.io.Serializable;
import java.util.UUID;

/** A specialty a guide picked, in their order. Replaced as a set. */
@Entity
@Table(name = "guide_profile_specialties")
@IdClass(GuideProfileSpecialty.Key.class)
public class GuideProfileSpecialty {

    @Id
    private UUID guideId;

    @Id
    private UUID specialtyId;

    private int position;

    protected GuideProfileSpecialty() {
    }

    public GuideProfileSpecialty(UUID guideId, UUID specialtyId, int position) {
        this.guideId = guideId;
        this.specialtyId = specialtyId;
        this.position = position;
    }

    public UUID getGuideId() {
        return guideId;
    }

    public UUID getSpecialtyId() {
        return specialtyId;
    }

    public int getPosition() {
        return position;
    }

    public record Key(UUID guideId, UUID specialtyId) implements Serializable {
    }
}
