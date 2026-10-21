package com.sahyatri.guide.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** One of the specialties guides pick from ("Winter & snow craft"). Admins keep the list. */
@Entity
@Table(name = "guide_specialties")
public class GuideSpecialty {

    @Id
    private UUID id;

    private String name;

    private int position;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    protected GuideSpecialty() {
    }

    public GuideSpecialty(String name, int position) {
        this.id = UUID.randomUUID();
        this.name = name;
        this.position = position;
        this.createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public int getPosition() {
        return position;
    }
}
