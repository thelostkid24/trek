package com.sahyatri.payment.service;

import com.sahyatri.catalog.entity.Departure;
import com.sahyatri.payment.entity.Payment;

import java.util.UUID;

/** Implemented by the booking feature: what a newly PAID payment means for its booking. */
public interface CaptureHandler {

    /** Called with the departure, booking and payment locked, in that order, and the payment just marked PAID. */
    void onCaptured(Departure departure, UUID bookingId, Payment payment);
}
