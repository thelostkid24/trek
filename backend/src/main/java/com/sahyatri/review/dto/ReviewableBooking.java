package com.sahyatri.review.dto;

import java.util.UUID;

/** The signed-in trekker's latest completed booking with a guide, which they review as a "Verified trek". */
public record ReviewableBooking(UUID bookingId, boolean reviewed) {
}
