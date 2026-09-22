package com.sahyatri.payment.service;

import com.sahyatri.auth.service.CurrentUser;
import com.sahyatri.booking.entity.Booking;
import com.sahyatri.booking.entity.BookingStatus;
import com.sahyatri.booking.repository.BookingRepository;
import com.sahyatri.catalog.entity.Departure;
import com.sahyatri.catalog.repository.DepartureRepository;
import com.sahyatri.common.exception.ApiException;
import com.sahyatri.common.util.HashingUtils;
import com.sahyatri.payment.dto.PaymentOrderResponse;
import com.sahyatri.payment.dto.PaymentResponse;
import com.sahyatri.payment.dto.VerifyPaymentRequest;
import com.sahyatri.payment.entity.Payment;
import com.sahyatri.payment.entity.PaymentMethod;
import com.sahyatri.payment.entity.PaymentStatus;
import com.sahyatri.payment.gateway.GatewayException;
import com.sahyatri.payment.gateway.GatewayPayment;
import com.sahyatri.payment.gateway.PaymentGateway;
import com.sahyatri.payment.repository.PaymentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Razorpay orders and the single place a payment becomes PAID (§7.4). Verify, webhook and reconciler all go
 * through {@link #applyCaptured}, which takes the row locks, so whichever arrives second does nothing.
 */
@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);
    private static final long TIMEOUT_BUFFER_SECONDS = 60;

    private final PaymentRepository payments;
    private final BookingRepository bookings;
    private final DepartureRepository departures;
    private final PaymentGateway gateway;
    private final CaptureHandler captureHandler;
    private final CurrentUser currentUser;
    private final ObjectMapper json;
    private final TransactionTemplate tx;

    public PaymentService(PaymentRepository payments, BookingRepository bookings, DepartureRepository departures,
                          PaymentGateway gateway, CaptureHandler captureHandler, CurrentUser currentUser,
                          ObjectMapper json, TransactionTemplate tx) {
        this.payments = payments;
        this.bookings = bookings;
        this.departures = departures;
        this.gateway = gateway;
        this.captureHandler = captureHandler;
        this.currentUser = currentUser;
        this.json = json;
        this.tx = tx;
    }

    /** Creates (or reuses) the Razorpay order for a live hold. The amount always comes from the booking. */
    public PaymentOrderResponse createOrder(UUID userId, UUID bookingId) {
        currentUser.require(userId);
        String keyId = gateway.keyId();
        return tx.execute(status -> {
            Booking booking = bookings.findByIdForUpdate(bookingId)
                    .filter(b -> b.getUserId().equals(userId))
                    .orElseThrow(PaymentService::bookingNotFound);
            Instant now = Instant.now();
            if (booking.getStatus() != BookingStatus.HELD || booking.isHoldExpired(now)) {
                throw ApiException.conflict("BOOKING_NOT_PAYABLE", "This booking can no longer be paid");
            }
            Payment payment = payments.findByBookingIdAndStatus(bookingId, PaymentStatus.CREATED).stream()
                    .findFirst()
                    .orElseGet(() -> {
                        UUID paymentId = UUID.randomUUID();
                        String orderId = gateway.createOrder(booking.getAmountPaise(), "INR", paymentId.toString(),
                                Map.of("booking_id", bookingId.toString(), "payment_id", paymentId.toString()));
                        return payments.saveAndFlush(Payment.created(paymentId, userId, bookingId, orderId,
                                booking.getAmountPaise()));
                    });
            long secondsLeft = Duration.between(now, booking.getHoldExpiresAt()).toSeconds();
            Departure departure = booking.getDeparture();
            return new PaymentOrderResponse(payment.getId(), bookingId, keyId, payment.getRazorpayOrderId(),
                    payment.getAmountPaise(), payment.getCurrency(),
                    departure.getTrack().getName() + " · " + departure.getStartDate() + " · " + booking.getSeats()
                            + (booking.getSeats() == 1 ? " seat" : " seats"),
                    Math.max(60, secondsLeft - TIMEOUT_BUFFER_SECONDS),
                    new PaymentOrderResponse.Prefill(booking.getContactName(), booking.getContactEmail(),
                            booking.getContactPhone()));
        });
    }

    /** Checkout's success callback. The signature proves the browser didn't make it up; Razorpay is then asked. */
    public PaymentResponse verify(UUID userId, UUID paymentId, VerifyPaymentRequest req) {
        currentUser.require(userId);
        Payment payment = payments.findByIdAndUserId(paymentId, userId).orElseThrow(PaymentService::paymentNotFound);
        String expected = HashingUtils.hmacSha256Hex(gateway.checkoutSigningKey(),
                payment.getRazorpayOrderId() + "|" + req.razorpayPaymentId());
        if (!payment.getRazorpayOrderId().equals(req.razorpayOrderId())
                || !HashingUtils.constantTimeEquals(expected, req.razorpaySignature())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "PAYMENT_SIGNATURE_INVALID",
                    "The payment could not be verified");
        }
        if (payment.getStatus() != PaymentStatus.PAID) {
            GatewayPayment remote = gateway.fetchPayment(req.razorpayPaymentId())
                    .orElseThrow(() -> new GatewayException("payment " + req.razorpayPaymentId() + " not found"));
            if (!payment.getRazorpayOrderId().equals(remote.orderId())) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "PAYMENT_SIGNATURE_INVALID",
                        "The payment could not be verified");
            }
            if (remote.captured()) {
                applyCaptured(paymentId, remote);
            }
        }
        return get(userId, paymentId);
    }

    public PaymentResponse get(UUID userId, UUID paymentId) {
        return payments.findByIdAndUserId(paymentId, userId)
                .map(this::toResponse)
                .orElseThrow(PaymentService::paymentNotFound);
    }

    /**
     * Marks the payment PAID and lets the booking react, under locks taken in the fixed order
     * departure → booking → payment. Idempotent. Joins an existing transaction.
     */
    public void applyCaptured(UUID paymentId, GatewayPayment remote) {
        tx.executeWithoutResult(status -> {
            // Read ids without loading entities, so the locked reads below return fresh rows.
            UUID bookingId = payments.findBookingId(paymentId).orElseThrow();
            UUID departureId = bookings.findDepartureId(bookingId).orElseThrow();
            Departure departure = departures.findByIdForUpdate(departureId).orElseThrow();
            bookings.findByIdForUpdate(bookingId).orElseThrow();
            Payment payment = payments.findByIdForUpdate(paymentId).orElseThrow();
            if (payment.getStatus() == PaymentStatus.PAID) {
                return;
            }
            if (remote.amountPaise() != payment.getAmountPaise()) {
                log.error("Captured amount {} for payment {} doesn't match {}; not applied",
                        remote.amountPaise(), paymentId, payment.getAmountPaise());
                return;
            }
            payment.markPaid(remote.id(), PaymentMethod.fromRazorpay(remote.method()),
                    json.writeValueAsString(remote.methodDetail()));
            payments.saveAndFlush(payment);
            captureHandler.onCaptured(departure, bookingId, payment);
            log.info("Payment {} captured ({})", paymentId, remote.id());
        });
    }

    public Optional<UUID> findIdByOrderId(String razorpayOrderId) {
        return payments.findIdByRazorpayOrderId(razorpayOrderId);
    }

    /** payment.failed: the order stays open for a retry; only the reason is recorded. */
    public void recordFailure(GatewayPayment remote) {
        tx.executeWithoutResult(status -> payments.findIdByRazorpayOrderId(remote.orderId())
                .flatMap(payments::findByIdForUpdate)
                .filter(p -> p.getStatus() == PaymentStatus.CREATED)
                .ifPresent(p -> {
                    p.recordFailure(remote.errorCode(), remote.errorDescription());
                    payments.save(p);
                }));
    }

    /** Called by the booking feature (booking lock held) or the reconciler once Razorpay shows no capture. */
    public void expire(Payment payment) {
        if (payment.getStatus() == PaymentStatus.CREATED) {
            payment.expire();
            payments.save(payment);
        }
    }

    public PaymentResponse toResponse(Payment p) {
        Map<String, String> detail = p.getMethodDetail() == null ? null
                : json.readValue(p.getMethodDetail(), new TypeReference<Map<String, String>>() {
                });
        return new PaymentResponse(p.getId(), p.getBookingId(), p.getStatus(), p.getAmountPaise(),
                p.getAmountRefundedPaise(), p.getMethod(), detail, p.getFailureReason(), p.getPaidAt(),
                p.getCreatedAt());
    }

    static ApiException paymentNotFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "PAYMENT_NOT_FOUND", "Payment not found");
    }

    static ApiException bookingNotFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "BOOKING_NOT_FOUND", "Booking not found");
    }
}
