package com.sahyatri.payment.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record CreateOrderRequest(@NotNull UUID bookingId) {
}
