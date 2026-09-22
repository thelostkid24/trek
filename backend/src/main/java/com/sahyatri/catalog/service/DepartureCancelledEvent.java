package com.sahyatri.catalog.service;

import java.util.UUID;

/**
 * Published inside the force-majeure cancellation transaction, with the departure row still locked, so
 * bookings are cancelled and refunded atomically with it (law 4).
 */
public record DepartureCancelledEvent(UUID departureId, UUID adminId) {
}
