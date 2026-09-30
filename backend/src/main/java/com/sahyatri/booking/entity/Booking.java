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

    /** Trek fee plus add-ons: what is charged, and what refunds are worked out on. */
    private long amountPaise;

    /** Add-ons: seats taking each, at the price per seat the booking was held at (null when not offered). */
    private int insuranceSeats;

    private Long insurancePricePaise;

    private int offloadingSeats;

    private Long offloadingPricePaise;

    private int transportSeats;

    private Long transportPricePaise;

    /** Add-ons total, kept apart from the trek fee: the guide and charity shares are on the trek fee only. */
    private long addonsPaise;

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
                               String contactEmail, AddonChoice addons, Instant holdExpiresAt) {
        Booking booking = new Booking();
        booking.id = UUID.randomUUID();
        booking.userId = userId;
        booking.departure = departure;
        booking.seats = seats;
        booking.contactName = contactName;
        booking.contactPhone = contactPhone;
        booking.contactEmail = contactEmail;
        booking.pricePaisePerSeat = departure.getPricePaise();
        booking.applyAddons(addons);
        booking.amountPaise = departure.getPricePaise() * seats + booking.addonsPaise;
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

    public void addTraveller(String fullName, String phone, LocalDate dateOfBirth, Gender gender,
                             TravellerAddons addons) {
        travellers.add(new BookingTraveller(this, travellers.size(), fullName, phone, dateOfBirth, gender, addons));
    }

    /**
     * Insurance is compulsory where the trek offers it: before paying, every seat is named and insured (ours or
     * their own policy).
     */
    public boolean isReadyToPay() {
        if (departure.getTrack().getInsurancePricePaise() == null) {
            return true;
        }
        return travellers.size() == seats && travellers.stream().allMatch(t -> t.getAddons().covered());
    }

    /** While held, the add-ons follow the travellers; the amount charged follows them. */
    public void reprice(AddonChoice addons) {
        applyAddons(addons);
        amountPaise = pricePaisePerSeat * seats + addonsPaise;
        updatedAt = Instant.now();
    }

    private void applyAddons(AddonChoice addons) {
        insuranceSeats = addons.insuranceSeats();
        insurancePricePaise = addons.insurancePricePaise();
        offloadingSeats = addons.offloadingSeats();
        offloadingPricePaise = addons.offloadingPricePaise();
        transportSeats = addons.transportSeats();
        transportPricePaise = addons.transportPricePaise();
        addonsPaise = addons.totalPaise();
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

    public int getInsuranceSeats() {
        return insuranceSeats;
    }

    public Long getInsurancePricePaise() {
        return insurancePricePaise;
    }

    public int getOffloadingSeats() {
        return offloadingSeats;
    }

    public Long getOffloadingPricePaise() {
        return offloadingPricePaise;
    }

    public int getTransportSeats() {
        return transportSeats;
    }

    public Long getTransportPricePaise() {
        return transportPricePaise;
    }

    public long getAddonsPaise() {
        return addonsPaise;
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
