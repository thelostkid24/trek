package com.sahyatri.payment.dto;

import com.sahyatri.payment.entity.PaymentMethod;
import com.sahyatri.payment.entity.PaymentStatus;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record PaymentResponse(
        UUID id,
        UUID bookingId,
        PaymentStatus status,
        long amountPaise,
        long amountRefundedPaise,
        PaymentMethod method,
        Map<String, String> methodDetail,
        String failureReason,
        Instant paidAt,
        Instant createdAt) {
}
