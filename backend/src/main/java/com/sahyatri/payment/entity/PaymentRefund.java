package com.sahyatri.payment.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** A refund we owe. Written PENDING first; sent to Razorpay after commit; finished by webhook or reconciler. */
@Entity
@Table(name = "payment_refunds")
public class PaymentRefund {

    @Id
    private UUID id;

    @Column(nullable = false, updatable = false)
    private UUID paymentId;

    private String razorpayRefundId;

    private long amountPaise;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RefundStatus status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private RefundKind kind;

    @Column(nullable = false, updatable = false)
    private String reason;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    protected PaymentRefund() {
    }

    public static PaymentRefund pending(UUID paymentId, long amountPaise, RefundKind kind, String reason) {
        PaymentRefund refund = new PaymentRefund();
        refund.id = UUID.randomUUID();
        refund.paymentId = paymentId;
        refund.amountPaise = amountPaise;
        refund.status = RefundStatus.PENDING;
        refund.kind = kind;
        refund.reason = reason;
        refund.createdAt = Instant.now();
        refund.updatedAt = refund.createdAt;
        return refund;
    }

    @PreUpdate
    void touch() {
        updatedAt = Instant.now();
    }

    public void accepted(String razorpayRefundId) {
        this.razorpayRefundId = razorpayRefundId;
    }

    public void processed() {
        this.status = RefundStatus.PROCESSED;
    }

    public void failed() {
        this.status = RefundStatus.FAILED;
    }

    public UUID getId() {
        return id;
    }

    public UUID getPaymentId() {
        return paymentId;
    }

    public String getRazorpayRefundId() {
        return razorpayRefundId;
    }

    public long getAmountPaise() {
        return amountPaise;
    }

    public RefundStatus getStatus() {
        return status;
    }

    public RefundKind getKind() {
        return kind;
    }

    public String getReason() {
        return reason;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
