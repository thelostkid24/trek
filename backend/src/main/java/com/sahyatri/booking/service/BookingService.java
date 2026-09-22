package com.sahyatri.booking.service;

import com.sahyatri.auth.dto.AuthSession;
import com.sahyatri.auth.entity.User;
import com.sahyatri.auth.repository.UserRepository;
import com.sahyatri.auth.service.AuthService;
import com.sahyatri.auth.service.CurrentUser;
import com.sahyatri.booking.dto.BookingContact;
import com.sahyatri.booking.dto.BookingDepartureRef;
import com.sahyatri.booking.dto.BookingRequest;
import com.sahyatri.booking.dto.BookingResponse;
import com.sahyatri.booking.dto.CancellationQuote;
import com.sahyatri.booking.dto.GuestBookingRequest;
import com.sahyatri.booking.dto.TravellerRequest;
import com.sahyatri.booking.dto.TravellerResponse;
import com.sahyatri.booking.dto.TravellersRequest;
import com.sahyatri.booking.entity.Booking;
import com.sahyatri.booking.entity.BookingStatus;
import com.sahyatri.booking.notify.BookingNotice;
import com.sahyatri.booking.repository.BookingRepository;
import com.sahyatri.catalog.dto.TrackBrief;
import com.sahyatri.catalog.entity.Departure;
import com.sahyatri.catalog.repository.DepartureRepository;
import com.sahyatri.catalog.service.CatalogService;
import com.sahyatri.common.audit.AuditLog;
import com.sahyatri.common.config.BookingProperties;
import com.sahyatri.common.exception.ApiException;
import com.sahyatri.payment.dto.RefundResponse;
import com.sahyatri.payment.entity.Payment;
import com.sahyatri.payment.entity.PaymentStatus;
import com.sahyatri.payment.entity.RefundKind;
import com.sahyatri.payment.repository.PaymentRefundRepository;
import com.sahyatri.payment.repository.PaymentRepository;
import com.sahyatri.payment.service.PaymentService;
import com.sahyatri.payment.service.RefundService;
import com.sahyatri.profile.service.TrekkerProfileService;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * A trekker's bookings (§7.6). Every seat change locks the departure row first, then the booking, then payments.
 * The user id always comes from the access token.
 */
@Service
public class BookingService {

    static final String ENTITY = "BOOKING";
    static final Set<BookingStatus> LIVE = EnumSet.of(BookingStatus.HELD, BookingStatus.CONFIRMED);

    private final BookingRepository bookings;
    private final DepartureRepository departures;
    private final PaymentRepository payments;
    private final PaymentRefundRepository refundRows;
    private final PaymentService paymentService;
    private final RefundService refunds;
    private final CatalogService catalog;
    private final UserRepository users;
    private final AuthService auth;
    private final CurrentUser currentUser;
    private final RefundPolicy refundPolicy;
    private final BookingProperties props;
    private final AuditLog audit;
    private final ApplicationEventPublisher events;

    public BookingService(BookingRepository bookings, DepartureRepository departures, PaymentRepository payments,
                          PaymentRefundRepository refundRows, PaymentService paymentService, RefundService refunds,
                          CatalogService catalog, UserRepository users, AuthService auth, CurrentUser currentUser,
                          RefundPolicy refundPolicy, BookingProperties props, AuditLog audit,
                          ApplicationEventPublisher events) {
        this.bookings = bookings;
        this.departures = departures;
        this.payments = payments;
        this.refundRows = refundRows;
        this.paymentService = paymentService;
        this.refunds = refunds;
        this.catalog = catalog;
        this.users = users;
        this.auth = auth;
        this.currentUser = currentUser;
        this.refundPolicy = refundPolicy;
        this.props = props;
        this.audit = audit;
        this.events = events;
    }

    /**
     * Holds seats for {@code app.bookings.hold-ttl} while a signed-in trekker pays. Contact fields left out come
     * from the account; travellers may come now or after payment.
     */
    @Transactional
    public BookingResponse create(UUID userId, BookingRequest req) {
        User user = currentUser.require(userId);
        BookingContact contact = new BookingContact(
                required("full_name", req.fullName(), user.getFullName()),
                required("phone", req.phone(), user.getPhone()),
                required("email", req.email(), user.getEmail()).toLowerCase(Locale.ROOT));
        List<TravellerRequest> travellers = req.travellers() == null ? List.of() : req.travellers();
        if (!travellers.isEmpty() && travellers.size() != req.seats()) {
            throw ApiException.validation("travellers", "must list exactly " + req.seats() + " traveller(s)");
        }
        return toResponse(hold(userId, req.departureId(), req.seats(), contact, travellers));
    }

    /**
     * Guest checkout: no password, no OTP. Creates a guest account and signs it in, so payment and the booking
     * pages work exactly as for any trekker. The guest never gets access to an existing account.
     */
    @Transactional
    public GuestBooking createForGuest(GuestBookingRequest req) {
        User guest = users.saveAndFlush(User.newGuest(req.fullName().trim()));
        BookingContact contact = new BookingContact(req.fullName().trim(), req.phone(),
                req.email().trim().toLowerCase(Locale.ROOT));
        Booking booking = hold(guest.getId(), req.departureId(), req.seats(), contact, List.of());
        return new GuestBooking(toResponse(booking), auth.firstSession(guest));
    }

    public record GuestBooking(BookingResponse booking, AuthSession session) {
    }

    /** Names every seat. Allowed on a live booking until the start date, so it can happen after payment. */
    @Transactional
    public BookingResponse updateTravellers(UUID userId, UUID bookingId, TravellersRequest req) {
        currentUser.require(userId);
        Locked locked = lock(userId, bookingId);
        Booking booking = locked.booking;
        if (!LIVE.contains(booking.getStatus()) || !catalog.today().isBefore(locked.departure.getStartDate())) {
            throw ApiException.conflict("TRAVELLERS_LOCKED", "Travellers can't be changed on this booking");
        }
        if (req.travellers().size() != booking.getSeats()) {
            throw ApiException.validation("travellers", "must list exactly " + booking.getSeats() + " traveller(s)");
        }
        checkAges(req.travellers(), locked.departure.getStartDate());
        booking.clearTravellers();
        bookings.saveAndFlush(booking);
        addTravellers(booking, req.travellers());
        bookings.saveAndFlush(booking);
        return toResponse(booking);
    }

    private Booking hold(UUID userId, UUID departureId, int seats, BookingContact contact,
                         List<TravellerRequest> travellers) {
        Departure departure = departures.findByIdForUpdate(departureId)
                .filter(d -> !d.isDraft())
                .orElseThrow(CatalogService::departureNotFound);
        checkAges(travellers, departure.getStartDate());
        if (!catalog.isOpenForBooking(departure)) {
            throw ApiException.conflict("DEPARTURE_NOT_BOOKABLE", "This departure isn't taking bookings");
        }
        bookings.findFirstByDepartureIdAndUserIdAndStatusIn(departure.getId(), userId, LIVE).ifPresent(b -> {
            throw new ApiException(HttpStatus.CONFLICT, "ALREADY_BOOKED", "You already have a booking on this departure",
                    Map.of("booking_id", b.getId().toString()));
        });
        if (departure.seatsLeft() < seats) {
            throw new ApiException(HttpStatus.CONFLICT, "NOT_ENOUGH_SEATS", "Not enough seats left",
                    Map.of("seats_left", departure.seatsLeft()));
        }

        Booking booking = Booking.hold(userId, departure, seats, contact.fullName(), contact.phone(), contact.email(),
                Instant.now().plus(props.holdTtl()));
        addTravellers(booking, travellers);
        departure.takeSeats(seats);
        departures.save(departure);
        bookings.saveAndFlush(booking);
        return booking;
    }

    private static void addTravellers(Booking booking, List<TravellerRequest> travellers) {
        for (TravellerRequest t : travellers) {
            booking.addTraveller(t.fullName().trim(), blankToNull(t.phone()), t.dateOfBirth(), t.gender());
        }
    }

    /** The request value, else the account's; neither → a validation error on {@code field}. */
    private static String required(String field, String given, String fromAccount) {
        String value = blankToNull(given);
        if (value == null) value = blankToNull(fromAccount);
        if (value == null) {
            throw ApiException.validation(field, "is required");
        }
        return value;
    }

    @Transactional(readOnly = true)
    public List<BookingResponse> list(UUID userId) {
        currentUser.require(userId);
        return bookings.findForUser(userId).stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public BookingResponse get(UUID userId, UUID bookingId) {
        currentUser.require(userId);
        return toResponse(owned(userId, bookingId));
    }

    /** The trekker gives the seats back before paying. */
    @Transactional
    public BookingResponse releaseHold(UUID userId, UUID bookingId) {
        currentUser.require(userId);
        Locked locked = lock(userId, bookingId);
        if (locked.booking.getStatus() != BookingStatus.HELD) {
            throw ApiException.conflict("BOOKING_NOT_HELD", "Only a booking awaiting payment can be released");
        }
        locked.booking.release();
        locked.departure.releaseSeats(locked.booking.getSeats());
        // Open orders stay CREATED: a payment already in flight is reconciled (and refunded if needed) later.
        bookings.saveAndFlush(locked.booking);
        return toResponse(locked.booking);
    }

    @Transactional(readOnly = true)
    public CancellationQuote quote(UUID userId, UUID bookingId) {
        currentUser.require(userId);
        return refundPolicy.quote(owned(userId, bookingId), catalog.today());
    }

    /** Trekker cancellation under the frozen refund tiers. Frees the seats for others. */
    @Transactional
    public BookingResponse cancel(UUID userId, UUID bookingId) {
        currentUser.require(userId);
        Locked locked = lock(userId, bookingId);
        Booking booking = locked.booking;
        CancellationQuote quote = refundPolicy.quote(booking, catalog.today());
        if (!quote.allowed()) {
            throw ApiException.conflict("BOOKING_NOT_CANCELLABLE", "This booking can't be cancelled");
        }
        booking.cancelByTrekker();
        locked.departure.releaseSeats(booking.getSeats());
        bookings.saveAndFlush(booking);

        long refunded = 0;
        if (quote.refundPaise() > 0) {
            Payment paid = paidPaymentsForUpdate(bookingId).stream()
                    .max(Comparator.comparingLong(Payment::refundablePaise))
                    .orElseThrow(() -> new IllegalStateException("Confirmed booking " + bookingId + " has no payment"));
            refunded = refunds.request(paid, quote.refundPaise(), RefundKind.TREKKER_CANCELLATION,
                            "Trekker cancelled " + quote.daysBeforeStart() + " day(s) before the start")
                    .map(r -> r.getAmountPaise())
                    .orElse(0L);
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("days_before_start", quote.daysBeforeStart());
        data.put("refund_bps", quote.refundBps());
        data.put("refund_paise", refunded);
        audit.record(userId, "BOOKING_CANCELLED", ENTITY, bookingId, data);
        events.publishEvent(new BookingNotice(bookingId, BookingNotice.Kind.CANCELLED_BY_TREKKER, refunded));
        return toResponse(booking);
    }

    /**
     * Ends a hold whose time is up. The reconciler calls this only after Razorpay showed no capture, so open
     * orders are closed too.
     */
    @Transactional
    public void expireHold(UUID bookingId) {
        UUID departureId = bookings.findDepartureId(bookingId).orElseThrow();
        Departure departure = departures.findByIdForUpdate(departureId).orElseThrow();
        Booking booking = bookings.findByIdForUpdate(bookingId).orElseThrow();
        if (booking.getStatus() != BookingStatus.HELD || !booking.isHoldExpired(Instant.now())) {
            return;
        }
        payments.findByBookingIdForUpdate(bookingId).forEach(paymentService::expire);
        booking.expire();
        departure.releaseSeats(booking.getSeats());
        bookings.saveAndFlush(booking);
    }

    List<Payment> paidPaymentsForUpdate(UUID bookingId) {
        return payments.findByBookingIdForUpdate(bookingId).stream()
                .filter(p -> p.getStatus() == PaymentStatus.PAID)
                .toList();
    }

    private Booking owned(UUID userId, UUID bookingId) {
        return bookings.findForUser(bookingId, userId).orElseThrow(BookingService::bookingNotFound);
    }

    private record Locked(Departure departure, Booking booking) {
    }

    /** Lock order: departure, then booking. Another trekker's booking is reported as not found. */
    private Locked lock(UUID userId, UUID bookingId) {
        UUID departureId = bookings.findDepartureId(bookingId).orElseThrow(BookingService::bookingNotFound);
        Departure departure = departures.findByIdForUpdate(departureId).orElseThrow();
        Booking booking = bookings.findByIdForUpdate(bookingId)
                .filter(b -> b.getUserId().equals(userId))
                .orElseThrow(BookingService::bookingNotFound);
        return new Locked(departure, booking);
    }

    private static void checkAges(List<TravellerRequest> travellers, LocalDate startDate) {
        for (int i = 0; i < travellers.size(); i++) {
            LocalDate dob = travellers.get(i).dateOfBirth();
            String field = "travellers[" + i + "].date_of_birth";
            if (dob.isAfter(startDate.minusYears(TrekkerProfileService.MIN_AGE))) {
                throw ApiException.validation(field, "must be at least " + TrekkerProfileService.MIN_AGE
                        + " on the trek date");
            }
            if (!dob.isAfter(startDate.minusYears(TrekkerProfileService.MAX_AGE + 1))) {
                throw ApiException.validation(field, "must be a real date of birth");
            }
        }
    }

    private BookingResponse toResponse(Booking b) {
        Departure d = b.getDeparture();
        BookingDepartureRef departure = new BookingDepartureRef(d.getId(), d.getStatus(), d.getStartDate(),
                d.getEndDate(), d.getTrack().getMeetingPoint(), d.getCancelReasonCode(), d.getCancelReasonNote(),
                TrackBrief.of(d.getTrack()), catalog.guideBrief(d));
        List<Payment> bookingPayments = payments.findByBookingIdOrderByCreatedAtDesc(b.getId());
        List<RefundResponse> refundList = bookingPayments.isEmpty() ? List.of()
                : refundRows.findByPaymentIdInOrderByCreatedAt(bookingPayments.stream().map(Payment::getId).toList())
                .stream().map(RefundResponse::of).toList();
        return new BookingResponse(b.getId(), b.getStatus(), b.getSeats(), b.getPricePaisePerSeat(),
                b.getAmountPaise(), new BookingContact(b.getContactName(), b.getContactPhone(), b.getContactEmail()),
                b.getTravellers().size() == b.getSeats(), b.getHoldExpiresAt(), b.getConfirmedAt(), b.getCancelledAt(), departure,
                b.getTravellers().stream().map(TravellerResponse::of).toList(),
                bookingPayments.stream().findFirst().map(paymentService::toResponse).orElse(null),
                refundList, refundPolicy.tiers(b), b.getCreatedAt());
    }

    static ApiException bookingNotFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "BOOKING_NOT_FOUND", "Booking not found");
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
