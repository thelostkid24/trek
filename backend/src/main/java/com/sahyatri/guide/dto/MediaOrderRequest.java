package com.sahyatri.guide.dto;

import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

/** Every one of the guide's items, in the new order. */
public record MediaOrderRequest(@NotNull List<@NotNull UUID> ids) {
}
