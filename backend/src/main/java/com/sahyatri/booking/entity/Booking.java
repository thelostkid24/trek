package com.sahyatri.booking.entity;

import com.sahyatri.catalog.entity.Departure;
import com.sahyatri.common.acquisition.Touch;
import jakarta.persistence.AttributeOverride;
import com.sahyatri.profile.entity.Gender;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import org.hibernate.annotations.ColumnTransformer;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Seats on a departure. HELD bookings count toward {@code departures.seats_taken} like confirmed ones; every
 * status change happens with the departure row locked first.
 */
@Entity
@Table(name = "bookings")
public class Booking {

    @Id
    private UUID id;

    @Column(nullable = false, updatable = false)
    private UUID userId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "departure_id", updatable = false)
    private Departure departure;

    private int seats;

    private long pricePaisePerSeat;

    private long amountPaise;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BookingStatus status;

    private String contactName;

    private String contactPhone;

    private String contactEmail;

    @Column(nullable = false)
    private Instant holdExpiresAt;

    private Instant confirmedAt;

    /** JSON array of refund tiers, frozen at confirmation. */
    @Column(columnDefinition = "jsonb")
    @ColumnTransformer(write = "?::jsonb")
    private String refundPolicy;

    private Instant cancelledAt;

    /** Last touch (§6.11): the visit that led to this booking. */
    @Embedded
    @AttributeOverride(name = "seenAt", column = @Column(name = "touch_seen_at", updatable = false))
    private Touch lastTouch;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    @OneToMany(mappedBy = "booking", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("position")
    private List<BookingTraveller> travellers = new ArrayList<>();

    protected Booking() {
    }

    public static Booking hold(UUID userId, Departure departure, int seats, String contactName, String contactPhone,
                               String contactEmail, Instant holdExpiresAt) {
        Booking booking = new Booking();
        booking.id = UUID.randomUUID();
        booking.userId = userId;
        booking.departure = departure;
        booking.seats = seats;
        booking.contactName = contactName;
        booking.contactPhone = contactPhone;
        booking.contactEmail = contactEmail;
        booking.pricePaisePerSeat = departure.getPricePaise();
        booking.amountPaise = departure.getPricePaise() * seats;
        booking.status = BookingStatus.HELD;
        booking.holdExpiresAt = holdExpiresAt;
        booking.createdAt = Instant.now();
        booking.updatedAt = booking.createdAt;
        return booking;
    }

    /** Set at hold time only. */
    public void setLastTouch(Touch lastTouch) {
        this.lastTouch = lastTouch;
    }

    public Touch getLastTouch() {
        return lastTouch;
    }

    @PreUpdate
    void touch() {
        updatedAt = Instant.now();
    }

    public void addTraveller(String fullName, String phone, LocalDate dateOfBirth, Gender gender) {
        travellers.add(new BookingTraveller(this, travellers.size(), fullName, phone, dateOfBirth, gender));
    }

    /** Flush before adding the new list: Hibernate inserts before it deletes, and positions are unique. */
    public void clearTravellers() {
        travellers.clear();
    }

    public void confirm(String refundPolicyJson) {
        status = BookingStatus.CONFIRMED;
        confirmedAt = Instant.now();
        refundPolicy = refundPolicyJson;
    }

    public void release() {
        status = BookingStatus.RELEASED;
    }

    public void expire() {
        status = BookingStatus.EXPIRED;
    }

    public void cancelByTrekker() {
        status = BookingStatus.CANCELLED_BY_TREKKER;
        cancelledAt = Instant.now();
    }

    public void cancelForForceMajeure() {
        status = BookingStatus.CANCELLED_FORCE_MAJEURE;
        cancelledAt = Instant.now();
    }

    public boolean isHoldExpired(Instant now) {
        return !holdExpiresAt.isAfter(now);
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public Departure getDeparture() {
        return departure;
    }

    public int getSeats() {
        return seats;
    }

    public long getPricePaisePerSeat() {
        return pricePaisePerSeat;
    }

    public long getAmountPaise() {
        return amountPaise;
    }

    public BookingStatus getStatus() {
        return status;
    }

    public String getContactName() {
        return contactName;
    }

    public String getContactPhone() {
        return contactPhone;
    }

    public String getContactEmail() {
        return contactEmail;
    }

    public Instant getHoldExpiresAt() {
        return holdExpiresAt;
    }

    public Instant getConfirmedAt() {
        return confirmedAt;
    }

    public String getRefundPolicy() {
        return refundPolicy;
    }

    public Instant getCancelledAt() {
        return cancelledAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public List<BookingTraveller> getTravellers() {
        return travellers;
    }
}
