package com.sahyatri.booking.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** A code emailed at checkout to prove the booking's contact email. Stored as an HMAC, never in the clear. */
@Entity
@Table(name = "booking_email_codes")
public class BookingEmailCode {

    @Id
    private UUID id;

    @Column(nullable = false, updatable = false)
    private String email;

    @Column(nullable = false, updatable = false)
    private String codeHash;

    @Column(nullable = false, updatable = false)
    private Instant expiresAt;

    @Column(nullable = false)
    private int attempts;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    /** Set when a seat hold used this code; a used code proves nothing more for checkout. */
    private Instant usedAt;

    protected BookingEmailCode() {
    }

    public BookingEmailCode(String email, String codeHash, Instant expiresAt) {
        this.id = UUID.randomUUID();
        this.email = email;
        this.codeHash = codeHash;
        this.expiresAt = expiresAt;
        this.createdAt = Instant.now();
    }

    public void recordFailedAttempt() {
        attempts++;
    }

    public void markUsed() {
        usedAt = Instant.now();
    }

    public boolean isUsed() {
        return usedAt != null;
    }

    public UUID getId() {
        return id;
    }

    public String getCodeHash() {
        return codeHash;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public int getAttempts() {
        return attempts;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
