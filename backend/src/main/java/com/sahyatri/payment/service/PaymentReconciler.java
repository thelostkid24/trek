package com.sahyatri.payment.service;

import com.sahyatri.booking.entity.BookingStatus;
import com.sahyatri.booking.repository.BookingRepository;
import com.sahyatri.booking.service.BookingService;
import com.sahyatri.common.config.BookingProperties;
import com.sahyatri.payment.entity.PaymentStatus;
import com.sahyatri.payment.entity.RefundStatus;
import com.sahyatri.payment.gateway.GatewayPayment;
import com.sahyatri.payment.gateway.PaymentGateway;
import com.sahyatri.payment.repository.PaymentRefundRepository;
import com.sahyatri.payment.repository.PaymentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Catches what webhooks miss (§7.4): expired holds, orders left open, refunds Razorpay hasn't accepted yet.
 * Razorpay is always asked before a hold is released, so a late capture is never lost.
 */
@Component
public class PaymentReconciler {

    private static final Logger log = LoggerFactory.getLogger(PaymentReconciler.class);
    private static final Duration REFUND_RETRY_AFTER = Duration.ofMinutes(2);

    private final BookingRepository bookings;
    private final PaymentRepository payments;
    private final PaymentRefundRepository refunds;
    private final PaymentGateway gateway;
    private final PaymentService paymentService;
    private final RefundService refundService;
    private final BookingService bookingService;
    private final BookingProperties bookingProps;
    private final TransactionTemplate tx;

    public PaymentReconciler(BookingRepository bookings, PaymentRepository payments, PaymentRefundRepository refunds,
                             PaymentGateway gateway, PaymentService paymentService, RefundService refundService,
                             BookingService bookingService, BookingProperties bookingProps, TransactionTemplate tx) {
        this.bookings = bookings;
        this.payments = payments;
        this.refunds = refunds;
        this.gateway = gateway;
        this.paymentService = paymentService;
        this.refundService = refundService;
        this.bookingService = bookingService;
        this.bookingProps = bookingProps;
        this.tx = tx;
    }

    @Scheduled(fixedDelayString = "${app.bookings.reconcile-interval:PT1M}",
            initialDelayString = "${app.bookings.reconcile-initial-delay:PT1M}")
    public void run() {
        Instant now = Instant.now();
        for (UUID bookingId : bookings.findExpiredHoldIds(BookingStatus.HELD, now)) {
            attempt("expired hold " + bookingId, () -> settleExpiredHold(bookingId));
        }
        for (UUID paymentId : payments.findStaleOrphanIds(PaymentStatus.CREATED, now.minus(bookingProps.holdTtl()))) {
            attempt("open order " + paymentId, () -> settleOpenOrder(paymentId));
        }
        for (UUID refundId : refunds.findUnsubmittedIds(RefundStatus.PENDING, now.minus(REFUND_RETRY_AFTER))) {
            attempt("refund " + refundId, () -> refundService.submit(refundId, true));
        }
    }

    /** A capture on any open order confirms the booking; otherwise the hold ends and the seats go back. */
    void settleExpiredHold(UUID bookingId) {
        for (UUID paymentId : payments.findIdsByBookingIdAndStatus(bookingId, PaymentStatus.CREATED)) {
            Optional<GatewayPayment> captured = capturedPayment(paymentId);
            if (captured.isPresent()) {
                paymentService.applyCaptured(paymentId, captured.get());
                return;
            }
        }
        bookingService.expireHold(bookingId);
    }

    /** An order whose booking is no longer held: apply a late capture, or close it. */
    void settleOpenOrder(UUID paymentId) {
        Optional<GatewayPayment> captured = capturedPayment(paymentId);
        if (captured.isPresent()) {
            paymentService.applyCaptured(paymentId, captured.get());
            return;
        }
        tx.executeWithoutResult(status -> payments.findByIdForUpdate(paymentId).ifPresent(paymentService::expire));
    }

    private Optional<GatewayPayment> capturedPayment(UUID paymentId) {
        String orderId = payments.findById(paymentId).orElseThrow().getRazorpayOrderId();
        return gateway.fetchOrderPayments(orderId).stream().filter(GatewayPayment::captured).findFirst();
    }

    private static void attempt(String what, Runnable work) {
        try {
            work.run();
        } catch (RuntimeException e) {
            log.warn("Reconciler: {} left for the next run: {}", what, e.getMessage());
        }
    }
}
