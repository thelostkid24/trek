package com.sahyatri.booking.notify;

import com.sahyatri.booking.entity.Booking;
import com.sahyatri.booking.repository.BookingRepository;
import com.sahyatri.catalog.entity.Departure;
import com.sahyatri.common.config.AppProperties;
import com.sahyatri.common.mail.MailTransport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Emails the booking contact after a booking is confirmed or cancelled. Runs after commit, so a mail failure is
 * logged and never undoes a payment or cancellation.
 */
@Component
public class BookingNotifier {

    private static final Logger log = LoggerFactory.getLogger(BookingNotifier.class);
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("EEE d MMM yyyy", Locale.ENGLISH);

    private final BookingRepository bookings;
    private final MailTransport mail;
    private final String frontendBaseUrl;

    public BookingNotifier(BookingRepository bookings, MailTransport mail, AppProperties props) {
        this.bookings = bookings;
        this.mail = mail;
        this.frontendBaseUrl = props.frontendBaseUrl();
    }

    @TransactionalEventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public void onNotice(BookingNotice notice) {
        try {
            Booking booking = bookings.findById(notice.bookingId()).orElseThrow();
            if (booking.getContactEmail() == null) {
                return;
            }
            Departure departure = booking.getDeparture();
            String trek = departure.getTrack().getName();
            String dates = DATE.format(departure.getStartDate())
                    + (departure.getEndDate().equals(departure.getStartDate()) ? "" : " – " + DATE.format(departure.getEndDate()));
            String link = frontendBaseUrl + "/account/bookings/" + booking.getId();
            String hi = "Hi " + firstName(booking.getContactName()) + ",\n\n";
            String sign = "\n\n— The Empty Valley team";
            switch (notice.kind()) {
                case CONFIRMED -> mail.send(booking.getContactEmail(), "Booked: " + trek + ", " + dates, hi
                        + "You're going! Your booking is confirmed.\n\n"
                        + "Trek: " + trek + "\nDates: " + dates
                        + "\nMeeting point: " + departure.getTrack().getMeetingPoint()
                        + "\nSeats: " + booking.getSeats() + "\nPaid: " + rupees(booking.getAmountPaise())
                        + "\n\nAdd everyone's traveller details before the trek: " + link + sign);
                case CANCELLED_BY_TREKKER -> mail.send(booking.getContactEmail(), "Cancelled: " + trek + ", " + dates, hi
                        + "Your booking for " + trek + " (" + dates + ") is cancelled.\n\n"
                        + refundLine(notice.refundPaise()) + "\n\nDetails: " + link + sign);
                case CANCELLED_FORCE_MAJEURE -> mail.send(booking.getContactEmail(),
                        "We've had to cancel " + trek + ", " + dates, hi
                        + "We're sorry. We've had to cancel the " + trek + " departure on " + dates
                        + (departure.getCancelReasonNote() == null ? "" : ": " + departure.getCancelReasonNote()) + ".\n\n"
                        + refundLine(notice.refundPaise()) + "\n\nDetails: " + link + sign);
            }
        } catch (RuntimeException e) {
            log.error("Could not email {} notice for booking {}", notice.kind(), notice.bookingId(), e);
        }
    }

    private static String refundLine(long refundPaise) {
        return refundPaise > 0
                ? "A refund of " + rupees(refundPaise) + " is on its way. It reaches UPI and wallets in a few days, "
                + "cards and netbanking in about 5–7 working days."
                : "No refund is due under the cancellation policy.";
    }

    static String rupees(long paise) {
        return paise % 100 == 0
                ? String.format(Locale.ENGLISH, "₹%,d", paise / 100)
                : String.format(Locale.ENGLISH, "₹%,.2f", paise / 100d);
    }

    private static String firstName(String fullName) {
        if (fullName == null || fullName.isBlank()) {
            return "there";
        }
        return fullName.trim().split("\\s+")[0];
    }
}
