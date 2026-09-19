package com.sahyatri.payment.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import org.hibernate.annotations.ColumnTransformer;

import java.time.Instant;
import java.util.UUID;

/** One Razorpay order for a booking. Card data never reaches this table (§7.4). */
@Entity
@Table(name = "payments")
public class Payment {

    @Id
    private UUID id;

    @Column(nullable = false, updatable = false)
    private UUID userId;

    @Column(nullable = false, updatable = false)
    private UUID bookingId;

    @Column(nullable = false, updatable = false)
    private String razorpayOrderId;

    private String razorpayPaymentId;

    private long amountPaise;

    private long amountRefundedPaise;

    @Column(nullable = false)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentStatus status;

    @Enumerated(EnumType.STRING)
    private PaymentMethod method;

    /** JSON object of display-safe fields (network, last4, bank, wallet, masked VPA). */
    @Column(columnDefinition = "jsonb")
    @ColumnTransformer(write = "?::jsonb")
    private String methodDetail;

    private String failureCode;

    private String failureReason;

    private Instant paidAt;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    protected Payment() {
    }

    public static Payment created(UUID id, UUID userId, UUID bookingId, String razorpayOrderId, long amountPaise) {
        Payment payment = new Payment();
        payment.id = id;
        payment.userId = userId;
        payment.bookingId = bookingId;
        payment.razorpayOrderId = razorpayOrderId;
        payment.amountPaise = amountPaise;
        payment.currency = "INR";
        payment.status = PaymentStatus.CREATED;
        payment.createdAt = Instant.now();
        payment.updatedAt = payment.createdAt;
        return payment;
    }

    @PreUpdate
    void touch() {
        updatedAt = Instant.now();
    }

    public void markPaid(String razorpayPaymentId, PaymentMethod method, String methodDetailJson) {
        this.status = PaymentStatus.PAID;
        this.razorpayPaymentId = razorpayPaymentId;
        this.method = method;
        this.methodDetail = methodDetailJson;
        this.failureCode = null;
        this.failureReason = null;
        this.paidAt = Instant.now();
    }

    /** Checkout lets the trekker retry inside the same order, so a failure only records why. */
    public void recordFailure(String code, String reason) {
        this.failureCode = code;
        this.failureReason = reason;
    }

    public void expire() {
        this.status = PaymentStatus.EXPIRED;
    }

    public long refundablePaise() {
        return amountPaise - amountRefundedPaise;
    }

    public void reserveRefund(long paise) {
        if (paise <= 0 || paise > refundablePaise()) {
            throw new IllegalStateException("Refund of " + paise + " exceeds what is left on payment " + id);
        }
        amountRefundedPaise += paise;
    }

    public void releaseRefund(long paise) {
        amountRefundedPaise = Math.max(0, amountRefundedPaise - paise);
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public UUID getBookingId() {
        return bookingId;
    }

    public String getRazorpayOrderId() {
        return razorpayOrderId;
    }

    public String getRazorpayPaymentId() {
        return razorpayPaymentId;
    }

    public long getAmountPaise() {
        return amountPaise;
    }

    public long getAmountRefundedPaise() {
        return amountRefundedPaise;
    }

    public String getCurrency() {
        return currency;
    }

    public PaymentStatus getStatus() {
        return status;
    }

    public PaymentMethod getMethod() {
        return method;
    }

    public String getMethodDetail() {
        return methodDetail;
    }

    public String getFailureCode() {
        return failureCode;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public Instant getPaidAt() {
        return paidAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
