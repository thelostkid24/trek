package com.sahyatri.booking;

import com.jayway.jsonpath.JsonPath;
import com.sahyatri.auth.AuthTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** A booking's contact email is proven with a checkout code unless it is the account's verified email (§7.6). */
class BookingEmailCodeTests extends AuthTestSupport {

    private ResultActions guestHold(UUID departure, String email, String code) throws Exception {
        return postJson("/api/public/bookings", """
                {"departure_id":"%s","seats":1,"full_name":"Neha Kulkarni","phone":"%s","email":"%s"%s}"""
                .formatted(departure, uniquePhone(), email, code == null ? "" : ",\"email_code\":\"" + code + "\""));
    }

    private ResultActions signedInHold(String token, UUID departure, String email, String code) throws Exception {
        return authed(post("/api/trekker/bookings"), token, """
                {"departure_id":"%s","seats":1,"full_name":"Asha Rao","phone":"%s"%s%s}"""
                .formatted(departure, uniquePhone(),
                        email == null ? "" : ",\"email\":\"" + email + "\"",
                        code == null ? "" : ",\"email_code\":\"" + code + "\""));
    }

    /** Gives back a hold made by {@code held}, so the account can hold seats again. */
    private void release(String token, ResultActions held) throws Exception {
        String body = held.andReturn().getResponse().getContentAsString();
        String id = JsonPath.read(body, body.contains("\"booking\"") ? "$.booking.id" : "$.id");
        authed(delete("/api/trekker/bookings/" + id + "/hold"), token, null).andExpect(status().isOk());
    }

    /** Lets the next code to this email go out now instead of after the resend wait. */
    private void skipResendWait(String email) {
        jdbc.update("UPDATE booking_email_codes SET created_at = created_at - interval '1 minute' WHERE email = ?",
                email);
    }

    private static String wrong(String code) {
        return code.equals("000000") ? "111111" : "000000";
    }

    @Test
    void aGuestNeedsTheCodeSentToTheirEmail() throws Exception {
        UUID departure = publishedDeparture();
        String email = uniqueEmail();
        int users = jdbc.queryForObject("SELECT count(*) FROM users", Integer.class);

        guestHold(departure, email, null)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("EMAIL_NOT_VERIFIED"));
        // A code was never sent to this address.
        guestHold(departure, email, "123456")
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.code").value("EMAIL_CODE_EXPIRED"));

        String code = bookingEmailCode(email);
        guestHold(departure, email, wrong(code))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("EMAIL_CODE_INVALID"))
                .andExpect(jsonPath("$.details.attempts_left").value(4));
        // Wrong codes count even though the booking failed.
        guestHold(departure, email, wrong(code))
                .andExpect(jsonPath("$.details.attempts_left").value(3));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM users", Integer.class)).isEqualTo(users);
        assertThat(seatsTaken(departure)).isZero();

        guestHold(departure, email, code).andExpect(status().isCreated());
    }

    @Test
    void wrongCodesLockTheCodeAndResendsAreThrottled() throws Exception {
        UUID departure = publishedDeparture();
        String email = uniqueEmail();
        String code = bookingEmailCode(email);
        for (int i = 0; i < 5; i++) {
            guestHold(departure, email, wrong(code)).andExpect(status().isBadRequest());
        }
        guestHold(departure, email, code)
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("EMAIL_CODE_TOO_MANY_ATTEMPTS"));

        postJson("/api/public/bookings/email-code", """
                {"email":"%s"}""".formatted(email))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("EMAIL_CODE_RATE_LIMITED"))
                .andExpect(jsonPath("$.details.retry_after").isNumber());
        postJson("/api/public/bookings/email-code", """
                {"email":"nope"}""").andExpect(status().isBadRequest());
    }

    @Test
    void aCorrectCodeSurvivesAFailedHold() throws Exception {
        UUID full = publishedDeparture(today().plusDays(30), 150_000, 1);
        UUID other = publishedDeparture();
        confirmedBooking(bookingTrekker(), full, 1);
        String email = uniqueEmail();
        String code = bookingEmailCode(email);

        guestHold(full, email, code).andExpect(status().isConflict());
        guestHold(other, email, code).andExpect(status().isCreated());
    }

    @Test
    void anUnverifiedAccountEmailIsVerifiedByTheCode() throws Exception {
        UUID departure = publishedDeparture();
        String email = uniqueEmail();
        String token = emailTrekker(email);

        signedInHold(token, departure, null, null)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("EMAIL_NOT_VERIFIED"));

        signedInHold(token, departure, null, bookingEmailCode(email))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.contact.email").value(email));
        authed(get("/api/auth/me"), token, null).andExpect(jsonPath("$.email_verified").value(true));
    }

    @Test
    void aVerifiedAccountBooksWithoutACodeButAnotherEmailNeedsOne() throws Exception {
        UUID departure = publishedDeparture();
        UUID second = publishedDeparture();
        String token = bookingTrekker();

        release(token, signedInHold(token, departure, null, null).andExpect(status().isCreated()));

        String other = uniqueEmail();
        signedInHold(token, second, other, null)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("EMAIL_NOT_VERIFIED"));
        signedInHold(token, second, other, bookingEmailCode(other))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.contact.email").value(other));
        // The account keeps its own email.
        authed(get("/api/auth/me"), token, null).andExpect(jsonPath("$.email").value(not(other)));
    }

    @Test
    void aReturningGuestGetsTheirProvenEmailOnTheAccount() throws Exception {
        UUID departure = publishedDeparture();
        UUID second = publishedDeparture();
        String email = uniqueEmail();
        ResultActions held = guestHold(departure, email, bookingEmailCode(email)).andExpect(status().isCreated());
        String guestToken = JsonPath.read(held.andReturn().getResponse().getContentAsString(), "$.auth.access_token");
        release(guestToken, held);

        String fresh = uniqueEmail();
        signedInHold(guestToken, second, fresh, bookingEmailCode(fresh)).andExpect(status().isCreated());
        authed(get("/api/auth/me"), guestToken, null)
                .andExpect(jsonPath("$.email").value(fresh))
                .andExpect(jsonPath("$.email_verified").value(true))
                .andExpect(jsonPath("$.guest").value(false));
    }

    @Test
    void aCodeHoldsSeatsOnlyOnce() throws Exception {
        UUID departure = publishedDeparture();
        UUID second = publishedDeparture();
        String email = uniqueEmail();
        String code = bookingEmailCode(email);

        guestHold(departure, email, code).andExpect(status().isCreated());
        guestHold(second, email, code)
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.code").value("EMAIL_CODE_USED"));
        assertThat(seatsTaken(second)).isZero();
    }

    @Test
    void parallelHoldsCantShareACode() throws Exception {
        String email = uniqueEmail();
        String code = bookingEmailCode(email);
        List<UUID> departures = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            departures.add(publishedDeparture());
        }

        CountDownLatch start = new CountDownLatch(1);
        List<Future<Integer>> results = new ArrayList<>();
        try (ExecutorService pool = Executors.newFixedThreadPool(departures.size())) {
            for (UUID departure : departures) {
                results.add(pool.submit(() -> {
                    start.await();
                    return guestHold(departure, email, code).andReturn().getResponse().getStatus();
                }));
            }
            start.countDown();
            int created = 0;
            for (Future<Integer> result : results) {
                if (result.get() == 201) created++;
            }
            assertThat(created).isEqualTo(1);
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM bookings WHERE contact_email = ?", Integer.class, email))
                .isEqualTo(1);
    }

    @Test
    void anAccountHasOneUnpaidHoldAtATime() throws Exception {
        UUID departure = publishedDeparture();
        UUID second = publishedDeparture();
        String token = bookingTrekker();

        ResultActions held = signedInHold(token, departure, null, null).andExpect(status().isCreated());
        String id = JsonPath.read(held.andReturn().getResponse().getContentAsString(), "$.id");
        signedInHold(token, second, null, null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("HOLD_PENDING"))
                .andExpect(jsonPath("$.details.booking_id").value(id));
        assertThat(seatsTaken(second)).isZero();

        release(token, held);
        signedInHold(token, second, null, null).andExpect(status().isCreated());
    }

    @Test
    void anEmailHasOneUnpaidHoldAtATimeAcrossGuestAccounts() throws Exception {
        UUID departure = publishedDeparture();
        UUID second = publishedDeparture();
        String email = uniqueEmail();
        guestHold(departure, email, bookingEmailCode(email)).andExpect(status().isCreated());

        skipResendWait(email);
        String code = bookingEmailCode(email);
        guestHold(second, email, code)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("HOLD_PENDING"))
                // Someone else's guest account: its booking isn't named.
                .andExpect(jsonPath("$.details.booking_id").doesNotExist());
        assertThat(seatsTaken(second)).isZero();

        // The refused hold didn't spend the code; once the first hold ends, it works.
        jdbc.update("UPDATE bookings SET hold_expires_at = now() WHERE contact_email = ?", email);
        guestHold(second, email, code).andExpect(status().isCreated());
    }
}
