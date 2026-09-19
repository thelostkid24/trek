package com.sahyatri.booking.dto;

import com.sahyatri.booking.entity.BookingStatus;
import com.sahyatri.payment.dto.PaymentResponse;
import com.sahyatri.payment.dto.RefundResponse;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record BookingResponse(
        UUID id,
        BookingStatus status,
        int seats,
        long pricePaisePerSeat,
        long amountPaise,
        BookingContact contact,
        // False until every seat has a named traveller; they can be added after payment.
        boolean travellersComplete,
        Instant holdExpiresAt,
        Instant confirmedAt,
        Instant cancelledAt,
        BookingDepartureRef departure,
        List<TravellerResponse> travellers,
        PaymentResponse payment,
        List<RefundResponse> refunds,
        List<RefundTierResponse> refundPolicy,
        Instant createdAt) {
}
