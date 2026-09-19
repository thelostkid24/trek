package com.sahyatri.payment;

import com.sahyatri.auth.AuthTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class WebhookTests extends AuthTestSupport {

    @Test
    void signatureIsRequiredAndChecked() throws Exception {
        String body = paymentEvent("payment.captured", "order_x", "pay_x", "captured", 100);
        mockMvc.perform(post("/api/webhooks/razorpay").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("WEBHOOK_SIGNATURE_INVALID"));
        mockMvc.perform(post("/api/webhooks/razorpay").contentType(MediaType.APPLICATION_JSON)
                        .header("X-Razorpay-Signature", "00ff").content(body))
                .andExpect(jsonPath("$.code").value("WEBHOOK_SIGNATURE_INVALID"));
        // Tampering after signing.
        mockMvc.perform(post("/api/webhooks/razorpay").contentType(MediaType.APPLICATION_JSON)
                        .header("X-Razorpay-Signature",
                                com.sahyatri.common.util.HashingUtils.hmacSha256Hex(WEBHOOK_SECRET, body))
                        .content(body.replace("100", "1")))
                .andExpect(jsonPath("$.code").value("WEBHOOK_SIGNATURE_INVALID"));
        // Unknown orders are acknowledged and ignored.
        webhook(body, "evt_" + UUID.randomUUID()).andExpect(status().isOk());
    }

    @Test
    void capturedEventConfirmsOnceEvenWhenDeliveredTwice() throws Exception {
        String token = bookingTrekker();
        UUID booking = hold(token, publishedDeparture(), 1);
        String[] order = order(token, booking);
        String body = paymentEvent("payment.captured", order[1], "pay_" + UUID.randomUUID().toString().substring(0, 8),
                "captured", 219_900);
        String eventId = "evt_" + UUID.randomUUID();

        webhook(body, eventId).andExpect(status().isOk());
        webhook(body, eventId).andExpect(status().isOk());
        webhook(paymentEvent("order.paid", order[1], "pay_other", "captured", 219_900), "evt_" + UUID.randomUUID())
                .andExpect(status().isOk());

        assertThat(bookingStatus(booking)).isEqualTo("CONFIRMED");
        assertThat(jdbc.queryForObject("SELECT method FROM payments WHERE id = ?", String.class,
                UUID.fromString(order[0]))).isEqualTo("NETBANKING");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM webhook_events WHERE event_id = ? AND processed_at IS NOT NULL",
                Integer.class, eventId)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM payment_refunds WHERE payment_id = ?", Integer.class,
                UUID.fromString(order[0]))).isZero();
    }

    @Test
    void failureThenRetryEndsPaid() throws Exception {
        String token = bookingTrekker();
        UUID booking = hold(token, publishedDeparture(), 1);
        String[] order = order(token, booking);

        webhook(paymentEvent("payment.failed", order[1], "pay_f1", "failed", 219_900), "evt_" + UUID.randomUUID())
                .andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT failure_reason FROM payments WHERE id = ?", String.class,
                UUID.fromString(order[0]))).isEqualTo("Bank declined");
        assertThat(bookingStatus(booking)).isEqualTo("HELD");

        webhook(paymentEvent("payment.captured", order[1], "pay_ok1", "captured", 219_900), "evt_" + UUID.randomUUID())
                .andExpect(status().isOk());
        assertThat(jdbc.queryForMap("SELECT status, failure_reason FROM payments WHERE id = ?",
                UUID.fromString(order[0])))
                .containsEntry("status", "PAID")
                .containsEntry("failure_reason", null);
        assertThat(bookingStatus(booking)).isEqualTo("CONFIRMED");
    }

    @Test
    void verifyAndWebhookRacingEndInOnePaid() throws Exception {
        String token = bookingTrekker();
        UUID departure = publishedDeparture();
        UUID booking = hold(token, departure, 2);
        String[] order = order(token, booking);
        var captured = gateway.capture(order[1]);
        String body = paymentEvent("payment.captured", order[1], captured.id(), "captured", captured.amountPaise());

        CountDownLatch start = new CountDownLatch(1);
        List<Future<Integer>> results = new ArrayList<>();
        try (ExecutorService pool = Executors.newFixedThreadPool(4)) {
            for (int i = 0; i < 2; i++) {
                results.add(pool.submit(() -> {
                    start.await();
                    return verifyPayment(token, order[0], order[1], captured.id()).andReturn().getResponse().getStatus();
                }));
                int n = i;
                results.add(pool.submit(() -> {
                    start.await();
                    return webhook(body, "evt_race_" + n + "_" + order[1]).andReturn().getResponse().getStatus();
                }));
            }
            start.countDown();
            for (Future<Integer> result : results) {
                assertThat(result.get()).isEqualTo(200);
            }
        }

        assertThat(bookingStatus(booking)).isEqualTo("CONFIRMED");
        assertThat(seatsTaken(departure)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM payment_refunds WHERE payment_id = ?", Integer.class,
                UUID.fromString(order[0]))).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_events WHERE entity_id = ? AND action = 'BOOKING_CONFIRMED'",
                Integer.class, booking)).isEqualTo(1);
    }
}
