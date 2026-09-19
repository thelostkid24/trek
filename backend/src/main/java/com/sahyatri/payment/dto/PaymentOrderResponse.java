package com.sahyatri.payment.dto;

import java.util.UUID;

/** Everything Checkout needs. Only the public key id leaves the server. */
public record PaymentOrderResponse(
        UUID paymentId,
        UUID bookingId,
        String keyId,
        String razorpayOrderId,
        long amountPaise,
        String currency,
        String description,
        long checkoutTimeoutSeconds,
        Prefill prefill) {

    public record Prefill(String name, String email, String contact) {
    }
}
