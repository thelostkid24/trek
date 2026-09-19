package com.sahyatri.booking.service;

import com.sahyatri.booking.entity.Booking;
import com.sahyatri.booking.entity.BookingStatus;
import com.sahyatri.booking.repository.BookingRepository;
import com.sahyatri.catalog.entity.Departure;
import com.sahyatri.catalog.repository.DepartureRepository;
import com.sahyatri.catalog.service.DepartureCancelledEvent;
import com.sahyatri.common.audit.AuditLog;
import com.sahyatri.payment.entity.Payment;
import com.sahyatri.payment.entity.RefundKind;
import com.sahyatri.payment.service.RefundService;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Law 4: cancelling a departure for force majeure cancels every confirmed booking with a full refund and
 * releases every hold. Runs synchronously inside the cancellation transaction, departure already locked.
 */
@Component
public class ForceMajeureCanceller {

    private final BookingRepository bookings;
    private final DepartureRepository departures;
    private final BookingService bookingService;
    private final RefundService refunds;
    private final AuditLog audit;

    public ForceMajeureCanceller(BookingRepository bookings, DepartureRepository departures,
                                 BookingService bookingService, RefundService refunds, AuditLog audit) {
        this.bookings = bookings;
        this.departures = departures;
        this.bookingService = bookingService;
        this.refunds = refunds;
        this.audit = audit;
    }

    @EventListener
    public void onDepartureCancelled(DepartureCancelledEvent event) {
        Departure departure = departures.findByIdForUpdate(event.departureId()).orElseThrow();
        String reason = "Departure cancelled (" + departure.getCancelReasonCode() + "): "
                + departure.getCancelReasonNote();
        for (Booking booking : bookings.findByDepartureForUpdate(event.departureId(), BookingService.LIVE)) {
            departure.releaseSeats(booking.getSeats());
            if (booking.getStatus() == BookingStatus.HELD) {
                booking.release();
                continue;
            }
            booking.cancelForForceMajeure();
            long refunded = 0;
            for (Payment payment : bookingService.paidPaymentsForUpdate(booking.getId())) {
                refunded += refunds.request(payment, payment.refundablePaise(), RefundKind.FORCE_MAJEURE, reason)
                        .map(r -> r.getAmountPaise())
                        .orElse(0L);
            }
            audit.record(event.adminId(), "BOOKING_CANCELLED_FORCE_MAJEURE", BookingService.ENTITY, booking.getId(),
                    Map.of("refund_paise", refunded));
        }
        bookings.flush();
    }
}
