package com.sahyatri.booking;

import com.jayway.jsonpath.JsonPath;
import com.sahyatri.auth.AuthTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GuestCheckoutTests extends AuthTestSupport {

    private static final String GUEST_BOOKINGS = "/api/public/bookings";

    private ResultActions guestHold(UUID departure, int seats, String phone, String email) throws Exception {
        return postJson(GUEST_BOOKINGS, """
                {"departure_id":"%s","seats":%d,"full_name":"Neha Kulkarni","phone":"%s","email":"%s"}"""
                .formatted(departure, seats, phone, email));
    }

    @Test
    void threeFieldsHoldSeatsAndSignTheGuestIn() throws Exception {
        UUID departure = publishedDeparture();
        String phone = uniquePhone();

        MvcResult result = guestHold(departure, 2, phone, "Neha@Example.com")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.booking.status").value("HELD"))
                .andExpect(jsonPath("$.booking.seats").value(2))
                .andExpect(jsonPath("$.booking.amount_paise").value(439_800))
                .andExpect(jsonPath("$.booking.contact.full_name").value("Neha Kulkarni"))
                .andExpect(jsonPath("$.booking.contact.phone").value(phone))
                .andExpect(jsonPath("$.booking.contact.email").value("neha@example.com"))
                .andExpect(jsonPath("$.booking.travellers", hasSize(0)))
                .andExpect(jsonPath("$.auth.access_token").exists())
                .andExpect(jsonPath("$.auth.is_new_user").value(true))
                .andExpect(jsonPath("$.auth.user.guest").value(true))
                .andExpect(jsonPath("$.auth.user.email").value(nullValue()))
                .andExpect(jsonPath("$.auth.user.auth_methods", hasSize(0)))
                .andReturn();
        assertThat(result.getResponse().getHeader(HttpHeaders.SET_COOKIE)).isNotNull();
        assertThat(seatsTaken(departure)).isEqualTo(2);

        String body = result.getResponse().getContentAsString();
        assertThat(Instant.parse(JsonPath.<String>read(body, "$.booking.hold_expires_at"))).isAfter(Instant.now());

        // The guest session pays and finishes the booking like any trekker.
        String token = JsonPath.read(body, "$.auth.access_token");
        UUID booking = UUID.fromString(JsonPath.read(body, "$.booking.id"));
        MvcResult orderResult = authed(post("/api/trekker/payments/orders"), token, """
                {"booking_id":"%s"}""".formatted(booking))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.prefill.name").value("Neha Kulkarni"))
                .andExpect(jsonPath("$.prefill.email").value("neha@example.com"))
                .andExpect(jsonPath("$.prefill.contact").value(phone))
                .andReturn();
        String orderBody = orderResult.getResponse().getContentAsString();
        String paymentId = JsonPath.read(orderBody, "$.payment_id");
        String orderId = JsonPath.read(orderBody, "$.razorpay_order_id");
        verifyPayment(token, paymentId, orderId, gateway.capture(orderId).id()).andExpect(status().isOk());
        assertThat(bookingStatus(booking)).isEqualTo("CONFIRMED");

        authed(put("/api/trekker/bookings/" + booking + "/travellers"), token, """
                {"travellers":%s}""".formatted(travellersJson(2)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.travellers_complete").value(true));
        authed(get("/api/trekker/bookings"), token, null).andExpect(jsonPath("$.items", hasSize(1)));
    }

    @Test
    void aGuestNamesEveryTravellerAndTheirAddonsBeforePaying() throws Exception {
        UUID departure = publishedDeparture(today().plusDays(40), 219_900, 6);
        jdbc.update("""
                UPDATE tracks SET insurance_price_paise = 50000, offloading = true, offloading_price_paise = 30000
                WHERE id = (SELECT track_id FROM departures WHERE id = ?)""", departure);

        postJson(GUEST_BOOKINGS, """
                {"departure_id":"%s","seats":2,"full_name":"Neha Kulkarni","phone":"%s","email":"%s","travellers":[
                  {"full_name":"Neha Kulkarni","date_of_birth":"1995-04-12","gender":"FEMALE","insurance":true,"offloading":true},
                  {"full_name":"Rohan Kulkarni","date_of_birth":"1993-01-30","gender":"MALE","insurance":true}]}"""
                .formatted(departure, uniquePhone(), uniqueEmail()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.booking.status").value("HELD"))
                .andExpect(jsonPath("$.booking.travellers_complete").value(true))
                .andExpect(jsonPath("$.booking.travellers[1].insurance").value(true))
                .andExpect(jsonPath("$.booking.addons.insurance_seats").value(2))
                .andExpect(jsonPath("$.booking.addons.offloading_seats").value(1))
                .andExpect(jsonPath("$.booking.amount_paise").value(439_800 + 2 * 50_000 + 30_000));

        // One traveller for two seats: refused, and no guest account is left behind.
        int users = jdbc.queryForObject("SELECT count(*) FROM users", Integer.class);
        postJson(GUEST_BOOKINGS, """
                {"departure_id":"%s","seats":2,"full_name":"Neha Kulkarni","phone":"%s","email":"%s","travellers":[
                  {"full_name":"Neha Kulkarni","date_of_birth":"1995-04-12","gender":"FEMALE","insurance":true}]}"""
                .formatted(departure, uniquePhone(), uniqueEmail()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.fields.travellers").exists());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM users", Integer.class)).isEqualTo(users);
    }

    @Test
    void aGuestNeverLandsInAnExistingAccount() throws Exception {
        UUID departure = publishedDeparture();
        String ownerEmail = uniqueEmail();
        String ownerToken = emailTrekker(ownerEmail);
        String ownerPhone = uniquePhone();
        jdbc.update("UPDATE users SET phone = ?, phone_verified_at = now() WHERE email = ?", ownerPhone, ownerEmail);

        MvcResult result = guestHold(departure, 1, ownerPhone, ownerEmail).andExpect(status().isCreated()).andReturn();
        String guestId = JsonPath.read(result.getResponse().getContentAsString(), "$.auth.user.id");
        assertThat(UUID.fromString(guestId)).isNotEqualTo(userIdByEmail(ownerEmail));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM users WHERE email = ?", Integer.class, ownerEmail))
                .isEqualTo(1);

        // The account holder's bookings are untouched.
        authed(get("/api/trekker/bookings"), ownerToken, null).andExpect(jsonPath("$.items", hasSize(0)));
    }

    @Test
    void guestFieldsAreValidatedAndNothingIsKeptOnFailure() throws Exception {
        UUID departure = publishedDeparture(today().plusDays(30), 150_000, 3);
        int users = jdbc.queryForObject("SELECT count(*) FROM users", Integer.class);

        postJson(GUEST_BOOKINGS, """
                {"departure_id":"%s","seats":1,"full_name":" ","phone":"12345","email":"nope"}""".formatted(departure))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.fields.full_name").exists())
                .andExpect(jsonPath("$.details.fields.phone").exists())
                .andExpect(jsonPath("$.details.fields.email").exists());

        guestHold(departure, 11, uniquePhone(), uniqueEmail())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.fields.seats").exists());

        guestHold(departure, 4, uniquePhone(), uniqueEmail())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("NOT_ENOUGH_SEATS"));
        guestHold(UUID.randomUUID(), 1, uniquePhone(), uniqueEmail())
                .andExpect(status().isNotFound());

        // A failed hold leaves no guest account behind.
        assertThat(jdbc.queryForObject("SELECT count(*) FROM users", Integer.class)).isEqualTo(users);
        assertThat(seatsTaken(departure)).isZero();
    }
}
