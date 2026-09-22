package com.sahyatri.payment.dto;

import com.sahyatri.payment.entity.PaymentRefund;
import com.sahyatri.payment.entity.RefundKind;
import com.sahyatri.payment.entity.RefundStatus;

import java.time.Instant;
import java.util.UUID;

public record RefundResponse(UUID id, long amountPaise, RefundStatus status, RefundKind kind, Instant createdAt) {

    public static RefundResponse of(PaymentRefund r) {
        return new RefundResponse(r.getId(), r.getAmountPaise(), r.getStatus(), r.getKind(), r.getCreatedAt());
    }
}
