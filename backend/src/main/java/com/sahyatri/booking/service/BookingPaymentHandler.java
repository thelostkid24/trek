package com.sahyatri.booking.service;

import com.sahyatri.booking.entity.Booking;
import com.sahyatri.booking.entity.BookingStatus;
import com.sahyatri.booking.repository.BookingRepository;
import com.sahyatri.catalog.entity.Departure;
import com.sahyatri.catalog.entity.DepartureStatus;
import com.sahyatri.catalog.service.CatalogService;
import com.sahyatri.common.audit.AuditLog;
import com.sahyatri.payment.entity.Payment;
import com.sahyatri.payment.entity.RefundKind;
import com.sahyatri.payment.service.CaptureHandler;
import com.sahyatri.payment.service.RefundService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

/** What a captured payment does to its booking, including captures that arrive after the hold ended. */
@Component
public class BookingPaymentHandler implements CaptureHandler {

    private static final Logger log = LoggerFactory.getLogger(BookingPaymentHandler.class);

    private final BookingRepository bookings;
    private final CatalogService catalog;
    private final RefundPolicy refundPolicy;
    private final RefundService refunds;
    private final AuditLog audit;

    public BookingPaymentHandler(BookingRepository bookings, CatalogService catalog, RefundPolicy refundPolicy,
                                 RefundService refunds, AuditLog audit) {
        this.bookings = bookings;
        this.catalog = catalog;
        this.refundPolicy = refundPolicy;
        this.refunds = refunds;
        this.audit = audit;
    }

    @Override
    public void onCaptured(Departure departure, UUID bookingId, Payment payment) {
        Booking booking = bookings.findById(bookingId).orElseThrow();
        switch (booking.getStatus()) {
            case HELD -> confirm(booking, false);
            case EXPIRED, RELEASED -> {
                if (canReclaimSeats(departure, booking)) {
                    departure.takeSeats(booking.getSeats());
                    confirm(booking, true);
                } else {
                    refundAll(payment, "Payment arrived after the seat hold ended");
                }
            }
            default -> refundAll(payment, "Duplicate payment for a booking that is already " + booking.getStatus());
        }
    }

    private boolean canReclaimSeats(Departure departure, Booking booking) {
        return departure.getStatus() == DepartureStatus.PUBLISHED
                && catalog.today().isBefore(departure.getStartDate())
                && departure.seatsLeft() >= booking.getSeats()
                && !bookings.existsByDepartureIdAndUserIdAndStatusIn(departure.getId(), booking.getUserId(),
                BookingService.LIVE);
    }

    private void confirm(Booking booking, boolean late) {
        booking.confirm(refundPolicy.currentTiersJson());
        bookings.saveAndFlush(booking);
        audit.record(null, "BOOKING_CONFIRMED", BookingService.ENTITY, booking.getId(), Map.of("late", late));
    }

    private void refundAll(Payment payment, String reason) {
        log.warn("Refunding payment {} in full: {}", payment.getId(), reason);
        refunds.request(payment, payment.refundablePaise(), RefundKind.LATE_CAPTURE, reason);
    }
}
