package com.sahyatri.booking.service;

import com.sahyatri.booking.dto.CancellationQuote;
import com.sahyatri.booking.dto.RefundTierResponse;
import com.sahyatri.booking.entity.Booking;
import com.sahyatri.booking.entity.BookingStatus;
import com.sahyatri.common.config.BookingProperties;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

/** Trekker-cancellation refunds (§7.6). Tiers are frozen onto a booking when it is confirmed. */
@Component
public class RefundPolicy {

    private final BookingProperties props;
    private final ObjectMapper json;

    public RefundPolicy(BookingProperties props, ObjectMapper json) {
        this.props = props;
        this.json = json;
    }

    /** Current configuration as the JSON stored on a booking. */
    public String currentTiersJson() {
        return json.writeValueAsString(props.refundTiers().stream()
                .map(t -> new RefundTierResponse(t.minDaysBefore(), t.refundBps()))
                .toList());
    }

    public List<RefundTierResponse> tiers(Booking booking) {
        if (booking.getRefundPolicy() == null) {
            return null;
        }
        return json.readValue(booking.getRefundPolicy(), new TypeReference<List<RefundTierResponse>>() {
        });
    }

    /** Cancellable only when confirmed and before the start date. */
    public CancellationQuote quote(Booking booking, LocalDate today) {
        long days = ChronoUnit.DAYS.between(today, booking.getDeparture().getStartDate());
        if (booking.getStatus() != BookingStatus.CONFIRMED || days < 1) {
            return new CancellationQuote(false, days, 0, 0);
        }
        int bps = tiers(booking).stream()
                .sorted((a, b) -> Integer.compare(b.minDaysBefore(), a.minDaysBefore()))
                .filter(t -> days >= t.minDaysBefore())
                .findFirst()
                .map(RefundTierResponse::refundBps)
                .orElse(0);
        return new CancellationQuote(true, days, bps, booking.getAmountPaise() * bps / 10_000);
    }
}
