package com.sahyatri.payment;

import com.sahyatri.auth.AuthTestSupport;
import com.sahyatri.payment.service.PaymentReconciler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PaymentTests extends AuthTestSupport {

    @Autowired
    PaymentReconciler reconciler;

    @Test
    void orderAmountComesFromTheBookingAndOpenOrderIsReused() throws Exception {
        String token = bookingTrekker();
        UUID booking = hold(token, publishedDeparture(), 2);

        authed(post("/api/trekker/payments/orders"), token, """
                {"booking_id":"%s","amount_paise":100}""".formatted(booking))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.key_id").value(FakePaymentGateway.KEY_ID))
                .andExpect(jsonPath("$.amount_paise").value(439_800))
                .andExpect(jsonPath("$.currency").value("INR"))
                .andExpect(jsonPath("$.booking_id").value(booking.toString()))
                .andExpect(jsonPath("$.checkout_timeout_seconds").isNumber())
                .andExpect(jsonPath("$.prefill.name").value("Asha Rao"))
                .andExpect(jsonPath("$.description").exists())
                .andExpect(jsonPath("$.key_secret").doesNotExist());

        String[] first = order(token, booking);
        String[] again = order(token, booking);
        assertThat(again).containsExactly(first);
        assertThat(gateway.order(first[1]).amountPaise()).isEqualTo(439_800);
        assertThat(gateway.order(first[1]).receipt()).isEqualTo(first[0]);
    }

    @Test
    void verifiedCaptureConfirmsTheBooking() throws Exception {
        String token = bookingTrekker();
        UUID booking = hold(token, publishedDeparture(), 1);
        String[] order = order(token, booking);
        String razorpayPaymentId = gateway.capture(order[1]).id();

        verifyPayment(token, order[0], order[1], razorpayPaymentId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAID"))
                .andExpect(jsonPath("$.method").value("CARD"))
                .andExpect(jsonPath("$.method_detail.card_last4").value("4242"))
                .andExpect(jsonPath("$.paid_at").exists());
        // Idempotent.
        verifyPayment(token, order[0], order[1], razorpayPaymentId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAID"));

        authed(get("/api/trekker/bookings/" + booking), token, null)
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.confirmed_at").exists())
                .andExpect(jsonPath("$.payment.status").value("PAID"))
                .andExpect(jsonPath("$.refund_policy[0].min_days_before").value(15))
                .andExpect(jsonPath("$.refund_policy[0].refund_bps").value(9000));
        authed(get("/api/trekker/payments/" + order[0]), token, null)
                .andExpect(jsonPath("$.status").value("PAID"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_events WHERE entity_id = ? AND action = ?",
                Integer.class, booking, "BOOKING_CONFIRMED")).isEqualTo(1);
    }

    @Test
    void badSignaturesAreRejected() throws Exception {
        String token = bookingTrekker();
        UUID booking = hold(token, publishedDeparture(), 1);
        String[] order = order(token, booking);
        String razorpayPaymentId = gateway.capture(order[1]).id();

        authed(post("/api/trekker/payments/" + order[0] + "/verify"), token, """
                {"razorpay_order_id":"%s","razorpay_payment_id":"%s","razorpay_signature":"deadbeef"}"""
                .formatted(order[1], razorpayPaymentId))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PAYMENT_SIGNATURE_INVALID"));
        // A signature for a different order doesn't carry over.
        verifyPayment(token, order[0], "order_other", razorpayPaymentId)
                .andExpect(jsonPath("$.code").value("PAYMENT_SIGNATURE_INVALID"));
        // A validly signed payment id that belongs to another order is refused too.
        String otherTrekker = bookingTrekker();
        String[] otherOrder = order(otherTrekker, hold(otherTrekker, publishedDeparture(), 1));
        verifyPayment(token, order[0], order[1], gateway.capture(otherOrder[1]).id())
                .andExpect(jsonPath("$.code").value("PAYMENT_SIGNATURE_INVALID"));

        assertThat(bookingStatus(booking)).isEqualTo("HELD");
    }

    @Test
    void notYetCapturedStaysCreated() throws Exception {
        String token = bookingTrekker();
        UUID booking = hold(token, publishedDeparture(), 1);
        String[] order = order(token, booking);

        verifyPayment(token, order[0], order[1], gateway.authorize(order[1]).id())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CREATED"));
        assertThat(bookingStatus(booking)).isEqualTo("HELD");
    }

    @Test
    void paymentsAreOwnerOnlyAndOnlyLiveHoldsArePayable() throws Exception {
        String token = bookingTrekker();
        UUID booking = hold(token, publishedDeparture(), 1);
        String[] order = order(token, booking);
        String other = bookingTrekker();

        authed(get("/api/trekker/payments/" + order[0]), other, null)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PAYMENT_NOT_FOUND"));
        verifyPayment(other, order[0], order[1], gateway.capture(order[1]).id())
                .andExpect(jsonPath("$.code").value("PAYMENT_NOT_FOUND"));
        authed(post("/api/trekker/payments/orders"), other, """
                {"booking_id":"%s"}""".formatted(booking))
                .andExpect(jsonPath("$.code").value("BOOKING_NOT_FOUND"));

        UUID released = hold(other, publishedDeparture(), 1);
        authed(delete("/api/trekker/bookings/" + released + "/hold"), other, null).andExpect(status().isOk());
        authed(post("/api/trekker/payments/orders"), other, """
                {"booking_id":"%s"}""".formatted(released))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("BOOKING_NOT_PAYABLE"));

        UUID expired = hold(other, publishedDeparture(), 1);
        jdbc.update("UPDATE bookings SET hold_expires_at = now() - interval '1 second' WHERE id = ?", expired);
        authed(post("/api/trekker/payments/orders"), other, """
                {"booking_id":"%s"}""".formatted(expired))
                .andExpect(jsonPath("$.code").value("BOOKING_NOT_PAYABLE"));
    }

    @Test
    void reconcilerExpiresUnpaidHoldsAndConfirmsMissedCaptures() throws Exception {
        UUID departure = publishedDeparture();
        String unpaidToken = bookingTrekker();
        UUID unpaid = hold(unpaidToken, departure, 2);
        String[] unpaidOrder = order(unpaidToken, unpaid);
        UUID noOrder = hold(bookingTrekker(), departure, 1);
        String paidToken = bookingTrekker();
        UUID paid = hold(paidToken, departure, 1);
        String[] paidOrder = order(paidToken, paid);
        gateway.capture(paidOrder[1]); // captured, but neither verify nor webhook arrived
        assertThat(seatsTaken(departure)).isEqualTo(4);

        jdbc.update("UPDATE bookings SET hold_expires_at = now() - interval '1 minute' WHERE departure_id = ?",
                departure);
        reconciler.run();

        assertThat(bookingStatus(unpaid)).isEqualTo("EXPIRED");
        assertThat(bookingStatus(noOrder)).isEqualTo("EXPIRED");
        assertThat(bookingStatus(paid)).isEqualTo("CONFIRMED");
        assertThat(paymentStatus(unpaidOrder[0])).isEqualTo("EXPIRED");
        assertThat(paymentStatus(paidOrder[0])).isEqualTo("PAID");
        assertThat(seatsTaken(departure)).isEqualTo(1);
    }

    @Test
    void lateCaptureConfirmsWhenSeatsAreStillFree() throws Exception {
        UUID departure = publishedDeparture();
        String token = bookingTrekker();
        UUID booking = hold(token, departure, 2);
        String[] order = order(token, booking);
        expireNow(booking);
        assertThat(bookingStatus(booking)).isEqualTo("EXPIRED");
        assertThat(seatsTaken(departure)).isZero();

        // UPI completes after the hold ended.
        verifyPayment(token, order[0], order[1], gateway.capture(order[1]).id())
                .andExpect(jsonPath("$.status").value("PAID"));

        assertThat(bookingStatus(booking)).isEqualTo("CONFIRMED");
        assertThat(seatsTaken(departure)).isEqualTo(2);
        assertThat(refundCount(order[0])).isZero();
    }

    @Test
    void lateCaptureIsRefundedWhenTheSeatsAreGone() throws Exception {
        UUID departure = publishedDeparture(today().plusDays(30), 150_000, 2);
        String token = bookingTrekker();
        UUID booking = hold(token, departure, 2);
        String[] order = order(token, booking);
        expireNow(booking);
        confirmedBooking(bookingTrekker(), departure, 2);

        verifyPayment(token, order[0], order[1], gateway.capture(order[1]).id())
                .andExpect(jsonPath("$.status").value("PAID"));

        assertThat(bookingStatus(booking)).isEqualTo("EXPIRED");
        assertThat(seatsTaken(departure)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT amount_paise FROM payment_refunds WHERE payment_id = ? AND kind = 'LATE_CAPTURE'",
                Long.class, UUID.fromString(order[0]))).isEqualTo(300_000L);
        authed(get("/api/trekker/bookings/" + booking), token, null)
                .andExpect(jsonPath("$.refunds[0].kind").value("LATE_CAPTURE"))
                .andExpect(jsonPath("$.payment.amount_refunded_paise").value(300_000));
    }

    @Test
    void capturedAmountMismatchIsNotApplied() throws Exception {
        String token = bookingTrekker();
        UUID booking = hold(token, publishedDeparture(), 1);
        String[] order = order(token, booking);

        verifyPayment(token, order[0], order[1], gateway.capture(order[1], 100).id())
                .andExpect(jsonPath("$.status").value("CREATED"));
        assertThat(bookingStatus(booking)).isEqualTo("HELD");
    }

    private void expireNow(UUID booking) {
        jdbc.update("UPDATE bookings SET hold_expires_at = now() - interval '1 minute' WHERE id = ?", booking);
        reconciler.run();
    }

    private String paymentStatus(String paymentId) {
        return jdbc.queryForObject("SELECT status FROM payments WHERE id = ?", String.class, UUID.fromString(paymentId));
    }

    private int refundCount(String paymentId) {
        return jdbc.queryForObject("SELECT count(*) FROM payment_refunds WHERE payment_id = ?", Integer.class,
                UUID.fromString(paymentId));
    }
}
