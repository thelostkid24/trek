package com.sahyatri.booking;

import com.sahyatri.auth.AuthTestSupport;
import com.sahyatri.payment.entity.RefundKind;
import com.sahyatri.payment.repository.PaymentRepository;
import com.sahyatri.payment.service.PaymentReconciler;
import com.sahyatri.payment.service.RefundService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CancellationTests extends AuthTestSupport {

    private static final String BOOKINGS = "/api/trekker/bookings/";

    @Autowired
    PaymentReconciler reconciler;

    @Autowired
    RefundService refundService;

    @Autowired
    PaymentRepository payments;

    @Autowired
    TransactionTemplate tx;

    @ParameterizedTest
    @CsvSource({"30, 9000", "15, 9000", "14, 5000", "7, 5000", "6, 0", "1, 0"})
    void quoteFollowsTheTiers(int daysBefore, int bps) throws Exception {
        String token = bookingTrekker();
        UUID departure = publishedDeparture(today().plusDays(40), 219_900, 6);
        UUID booking = confirmedBooking(token, departure, 2);
        moveStart(departure, today().plusDays(daysBefore));

        authed(get(BOOKINGS + booking + "/cancellation-quote"), token, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allowed").value(true))
                .andExpect(jsonPath("$.days_before_start").value(daysBefore))
                .andExpect(jsonPath("$.refund_bps").value(bps))
                .andExpect(jsonPath("$.refund_paise").value(439_800L * bps / 10_000));
    }

    @Test
    void cancellingFreesSeatsAndRefundsTheTier() throws Exception {
        String token = bookingTrekker();
        UUID departure = publishedDeparture(today().plusDays(20), 219_900, 2);
        UUID booking = confirmedBooking(token, departure, 2);
        assertThat(seatsTaken(departure)).isEqualTo(2);

        authed(post(BOOKINGS + booking + "/cancel"), token, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED_BY_TREKKER"))
                .andExpect(jsonPath("$.cancelled_at").exists())
                .andExpect(jsonPath("$.refunds[0].amount_paise").value(395_820))
                .andExpect(jsonPath("$.refunds[0].kind").value("TREKKER_CANCELLATION"))
                .andExpect(jsonPath("$.payment.amount_refunded_paise").value(395_820));

        assertThat(seatsTaken(departure)).isZero();
        // Sent to Razorpay after commit.
        String paymentId = jdbc.queryForObject("SELECT razorpay_payment_id FROM payments WHERE booking_id = ?",
                String.class, booking);
        assertThat(gateway.refundsFor(paymentId)).singleElement()
                .satisfies(r -> assertThat(r.amountPaise()).isEqualTo(395_820));
        assertThat(jdbc.queryForObject("SELECT razorpay_refund_id FROM payment_refunds r JOIN payments p ON p.id = r.payment_id WHERE p.booking_id = ?",
                String.class, booking)).startsWith("rfnd_");

        // The seats can be booked again, and a second cancel is refused.
        hold(bookingTrekker(), departure, 2);
        authed(post(BOOKINGS + booking + "/cancel"), token, null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("BOOKING_NOT_CANCELLABLE"));
        authed(get(BOOKINGS + booking + "/cancellation-quote"), token, null)
                .andExpect(jsonPath("$.allowed").value(false));
    }

    @Test
    void lateCancellationRefundsNothingButFreesSeats() throws Exception {
        String token = bookingTrekker();
        UUID departure = publishedDeparture();
        UUID booking = confirmedBooking(token, departure, 1);
        moveStart(departure, today().plusDays(3));

        authed(post(BOOKINGS + booking + "/cancel"), token, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.refunds").isEmpty());
        assertThat(seatsTaken(departure)).isZero();
        assertThat(jdbc.queryForObject("SELECT data->>'refund_paise' FROM audit_events WHERE entity_id = ? AND action = 'BOOKING_CANCELLED'",
                String.class, booking)).isEqualTo("0");
    }

    @Test
    void cannotCancelOnOrAfterTheStartOrBeforePaying() throws Exception {
        String token = bookingTrekker();
        UUID departure = publishedDeparture();
        UUID booking = confirmedBooking(token, departure, 1);
        moveStart(departure, today());

        authed(get(BOOKINGS + booking + "/cancellation-quote"), token, null)
                .andExpect(jsonPath("$.allowed").value(false))
                .andExpect(jsonPath("$.days_before_start").value(0));
        authed(post(BOOKINGS + booking + "/cancel"), token, null)
                .andExpect(jsonPath("$.code").value("BOOKING_NOT_CANCELLABLE"));

        String other = bookingTrekker();
        UUID held = hold(other, publishedDeparture(), 1);
        authed(post(BOOKINGS + held + "/cancel"), other, null)
                .andExpect(jsonPath("$.code").value("BOOKING_NOT_CANCELLABLE"));
        assertThat(bookingStatus(booking)).isEqualTo("CONFIRMED");
    }

    @Test
    void forceMajeureRefundsEveryPaidBookingInFull() throws Exception {
        UUID departure = publishedDeparture(today().plusDays(10), 219_900, 6);
        String a = bookingTrekker();
        String b = bookingTrekker();
        UUID first = confirmedBooking(a, departure, 2);
        UUID second = confirmedBooking(b, departure, 1);
        String c = bookingTrekker();
        UUID held = hold(c, departure, 2);
        String[] heldOrder = order(c, held);
        assertThat(seatsTaken(departure)).isEqualTo(5);

        authed(post("/api/admin/departures/" + departure + "/cancel"), adminToken(), """
                {"reason_code":"PERMIT_DENIED","reason_note":"Forest department closed the trail"}""")
                .andExpect(status().isOk());

        assertThat(bookingStatus(first)).isEqualTo("CANCELLED_FORCE_MAJEURE");
        assertThat(bookingStatus(second)).isEqualTo("CANCELLED_FORCE_MAJEURE");
        assertThat(bookingStatus(held)).isEqualTo("RELEASED");
        assertThat(seatsTaken(departure)).isZero();
        authed(get(BOOKINGS + first), a, null)
                .andExpect(jsonPath("$.departure.status").value("CANCELLED"))
                .andExpect(jsonPath("$.departure.cancel_reason_code").value("PERMIT_DENIED"))
                .andExpect(jsonPath("$.refunds[0].kind").value("FORCE_MAJEURE"))
                .andExpect(jsonPath("$.refunds[0].amount_paise").value(439_800))
                .andExpect(jsonPath("$.payment.amount_refunded_paise").value(439_800));
        authed(get(BOOKINGS + second), b, null)
                .andExpect(jsonPath("$.refunds[0].amount_paise").value(219_900));
        authed(post(BOOKINGS + first + "/cancel"), a, null)
                .andExpect(jsonPath("$.code").value("BOOKING_NOT_CANCELLABLE"));

        // The released hold's trekker pays anyway: refunded in full.
        verifyPayment(c, heldOrder[0], heldOrder[1], gateway.capture(heldOrder[1]).id())
                .andExpect(jsonPath("$.status").value("PAID"));
        assertThat(bookingStatus(held)).isEqualTo("RELEASED");
        authed(get(BOOKINGS + held), c, null)
                .andExpect(jsonPath("$.refunds[0].kind").value("LATE_CAPTURE"))
                .andExpect(jsonPath("$.refunds[0].amount_paise").value(439_800));
        assertThat(seatsTaken(departure)).isZero();
    }

    @Test
    void forceMajeureAfterAPartialRefundReturnsTheRest() throws Exception {
        UUID departure = publishedDeparture(today().plusDays(20), 200_000, 6);
        String token = bookingTrekker();
        UUID booking = confirmedBooking(token, departure, 1);
        UUID paymentId = jdbc.queryForObject("SELECT id FROM payments WHERE booking_id = ?", UUID.class, booking);

        tx.executeWithoutResult(s -> {
            var payment = payments.findByIdForUpdate(paymentId).orElseThrow();
            assertThat(refundService.request(payment, 50_000, RefundKind.LATE_CAPTURE, "test"))
                    .isPresent();
            // Never more than what is left.
            assertThat(refundService.request(payment, 999_999, RefundKind.LATE_CAPTURE, "test")
                    .orElseThrow().getAmountPaise()).isEqualTo(150_000);
            assertThat(refundService.request(payment, 1, RefundKind.LATE_CAPTURE, "test"))
                    .isEmpty();
        });
        assertThat(jdbc.queryForObject("SELECT amount_refunded_paise FROM payments WHERE id = ?", Long.class, paymentId))
                .isEqualTo(200_000L);
    }

    @Test
    void refundWebhooksFinishRefundsAndFailuresReleaseTheReservation() throws Exception {
        String token = bookingTrekker();
        UUID departure = publishedDeparture(today().plusDays(20), 200_000, 6);
        UUID booking = confirmedBooking(token, departure, 1);
        authed(post(BOOKINGS + booking + "/cancel"), token, null).andExpect(status().isOk());
        Map<String, Object> refund = jdbc.queryForMap("""
                SELECT r.id, r.razorpay_refund_id, p.razorpay_payment_id, p.id AS payment_id FROM payment_refunds r
                JOIN payments p ON p.id = r.payment_id WHERE p.booking_id = ?""", booking);

        webhook(refundEvent("refund.failed", (String) refund.get("razorpay_refund_id"),
                (String) refund.get("razorpay_payment_id"), 180_000, "failed"), "evt_" + UUID.randomUUID())
                .andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT status FROM payment_refunds WHERE id = ?", String.class, refund.get("id")))
                .isEqualTo("FAILED");
        assertThat(jdbc.queryForObject("SELECT amount_refunded_paise FROM payments WHERE id = ?", Long.class,
                refund.get("payment_id"))).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_events WHERE entity_id = ? AND action = 'REFUND_FAILED'",
                Integer.class, refund.get("payment_id"))).isEqualTo(1);

        // A processed event for a finished refund changes nothing.
        webhook(refundEvent("refund.processed", (String) refund.get("razorpay_refund_id"),
                (String) refund.get("razorpay_payment_id"), 180_000, "processed"), "evt_" + UUID.randomUUID())
                .andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT status FROM payment_refunds WHERE id = ?", String.class, refund.get("id")))
                .isEqualTo("FAILED");
    }

    @Test
    void refundsTheGatewayRejectedAreRetriedOnce() throws Exception {
        String token = bookingTrekker();
        UUID departure = publishedDeparture(today().plusDays(20), 200_000, 6);
        UUID booking = confirmedBooking(token, departure, 1);

        gateway.failRefunds(1);
        authed(post(BOOKINGS + booking + "/cancel"), token, null).andExpect(status().isOk());
        UUID refundId = jdbc.queryForObject("""
                SELECT r.id FROM payment_refunds r JOIN payments p ON p.id = r.payment_id WHERE p.booking_id = ?""",
                UUID.class, booking);
        assertThat(jdbc.queryForObject("SELECT razorpay_refund_id FROM payment_refunds WHERE id = ?", String.class,
                refundId)).isNull();

        jdbc.update("UPDATE payment_refunds SET created_at = now() - interval '5 minutes' WHERE id = ?", refundId);
        reconciler.run();
        reconciler.run();

        String razorpayPaymentId = jdbc.queryForObject("SELECT razorpay_payment_id FROM payments WHERE booking_id = ?",
                String.class, booking);
        assertThat(gateway.refundsFor(razorpayPaymentId)).hasSize(1);
        String razorpayRefundId = jdbc.queryForObject("SELECT razorpay_refund_id FROM payment_refunds WHERE id = ?",
                String.class, refundId);
        assertThat(razorpayRefundId).isEqualTo(gateway.refundsFor(razorpayPaymentId).getFirst().id());

        webhook(refundEvent("refund.processed", razorpayRefundId, razorpayPaymentId, 180_000, "processed"),
                "evt_" + UUID.randomUUID()).andExpect(status().isOk());
        authed(get(BOOKINGS + booking), token, null).andExpect(jsonPath("$.refunds[0].status").value("PROCESSED"));
    }

    private void moveStart(UUID departure, LocalDate start) {
        jdbc.update("UPDATE departures SET start_date = ?, end_date = ? WHERE id = ?", start, start.plusDays(1), departure);
    }
}
