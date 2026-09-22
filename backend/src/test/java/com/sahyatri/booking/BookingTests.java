package com.sahyatri.booking;

import com.sahyatri.auth.AuthTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class BookingTests extends AuthTestSupport {

    private static final String BOOKINGS = "/api/trekker/bookings";

    @Test
    void holdTakesSeatsAtTheFrozenPrice() throws Exception {
        UUID departure = publishedDeparture();
        String token = bookingTrekker();

        UUID booking = hold(token, departure, 2);

        authed(get(BOOKINGS + "/" + booking), token, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("HELD"))
                .andExpect(jsonPath("$.seats").value(2))
                .andExpect(jsonPath("$.price_paise_per_seat").value(219_900))
                .andExpect(jsonPath("$.amount_paise").value(439_800))
                .andExpect(jsonPath("$.hold_expires_at").exists())
                .andExpect(jsonPath("$.travellers[*].full_name", contains("Traveller 0", "Traveller 1")))
                .andExpect(jsonPath("$.departure.id").value(departure.toString()))
                .andExpect(jsonPath("$.departure.track.name").value("Rajmachi Fort"))
                .andExpect(jsonPath("$.departure.meeting_point").value("Lonavala station"))
                .andExpect(jsonPath("$.payment").value(nullValue()))
                .andExpect(jsonPath("$.refund_policy").value(nullValue()));
        mockMvc.perform(get("/api/public/departures/" + departure)).andExpect(jsonPath("$.seats_left").value(4));

        // A later price change can't touch the held booking.
        jdbc.update("UPDATE departures SET price_paise = 999900 WHERE id = ?", departure);
        authed(get(BOOKINGS), token, null)
                .andExpect(jsonPath("$.items[0].id").value(booking.toString()))
                .andExpect(jsonPath("$.items[0].amount_paise").value(439_800));
    }

    @Test
    void contactComesFromTheRequestOrTheAccount() throws Exception {
        UUID departure = publishedDeparture();
        String email = uniqueEmail();
        String token = emailTrekker(email);

        // No phone on the account and none given: the WhatsApp number is the one thing missing.
        authed(post(BOOKINGS), token, """
                {"departure_id":"%s","seats":1}""".formatted(departure))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.fields.phone").exists());
        assertThat(seatsTaken(departure)).isZero();

        // No emergency contact or verified phone needed, and no travellers yet.
        authed(post(BOOKINGS), token, """
                {"departure_id":"%s","seats":2,"phone":"+919876501234"}""".formatted(departure))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("HELD"))
                .andExpect(jsonPath("$.contact.full_name").value("Asha Rao"))
                .andExpect(jsonPath("$.contact.phone").value("+919876501234"))
                .andExpect(jsonPath("$.contact.email").value(email))
                .andExpect(jsonPath("$.travellers", hasSize(0)))
                .andExpect(jsonPath("$.travellers_complete").value(false));
        assertThat(seatsTaken(departure)).isEqualTo(2);
    }

    @Test
    void travellersCanBeNamedAfterPayment() throws Exception {
        UUID departure = publishedDeparture();
        String token = bookingTrekker();
        UUID booking = hold(token, departure, 2);
        String path = BOOKINGS + "/" + booking + "/travellers";
        jdbc.update("DELETE FROM booking_travellers WHERE booking_id = ?", booking);

        authed(put(path), token, """
                {"travellers":%s}""".formatted(travellersJson(1)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.fields.travellers").exists());

        String[] order = order(token, booking);
        verifyPayment(token, order[0], order[1], gateway.capture(order[1]).id()).andExpect(status().isOk());

        authed(put(path), token, """
                {"travellers":%s}""".formatted(travellersJson(2)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.travellers_complete").value(true));
        // Replacing the list again keeps positions unique.
        authed(put(path), token, """
                {"travellers":[
                 {"full_name":"Asha","date_of_birth":"1995-04-12","gender":"FEMALE"},
                 {"full_name":"Ravi","date_of_birth":"1990-01-01","gender":"MALE"}]}""")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.travellers[*].full_name", contains("Asha", "Ravi")));

        authed(put(path), token, """
                {"travellers":[
                 {"full_name":"Asha","date_of_birth":"1995-04-12","gender":"FEMALE"},
                 {"full_name":"Kid","date_of_birth":"2015-01-01","gender":"MALE"}]}""")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.fields['travellers[1].date_of_birth']").exists());

        authed(put(path), bookingTrekker(), """
                {"travellers":%s}""".formatted(travellersJson(2)))
                .andExpect(status().isNotFound());

        jdbc.update("UPDATE bookings SET status = 'RELEASED', confirmed_at = NULL, refund_policy = NULL WHERE id = ?",
                booking);
        authed(put(path), token, """
                {"travellers":%s}""".formatted(travellersJson(2)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TRAVELLERS_LOCKED"));
    }

    @Test
    void groupsGoUpToTen() throws Exception {
        UUID departure = publishedDeparture(today().plusDays(30), 150_000, 10);
        hold(bookingTrekker(), departure, 10);
        assertThat(seatsTaken(departure)).isEqualTo(10);
    }

    @Test
    void travellersAreValidated() throws Exception {
        UUID departure = publishedDeparture();
        String token = bookingTrekker();

        authed(post(BOOKINGS), token, """
                {"departure_id":"%s","seats":2,"travellers":%s}""".formatted(departure, travellersJson(1)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.fields.travellers").exists());

        String minor = "2015-01-01";
        authed(post(BOOKINGS), token, """
                {"departure_id":"%s","seats":2,"travellers":[
                 {"full_name":"Asha","date_of_birth":"1995-04-12","gender":"FEMALE"},
                 {"full_name":"Kid","date_of_birth":"%s","gender":"MALE"}]}""".formatted(departure, minor))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.fields['travellers[1].date_of_birth']").exists());

        authed(post(BOOKINGS), token, """
                {"departure_id":"%s","seats":1,"travellers":[
                 {"full_name":" ","phone":"12345","date_of_birth":"1995-04-12","gender":"FEMALE"}]}"""
                .formatted(departure))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.fields['travellers[0].full_name']").exists())
                .andExpect(jsonPath("$.details.fields['travellers[0].phone']").exists());

        authed(post(BOOKINGS), token, """
                {"departure_id":"%s","seats":11,"travellers":%s}""".formatted(departure, travellersJson(11)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.fields.seats").exists());
        assertThat(seatsTaken(departure)).isZero();
    }

    @Test
    void onlyOpenDeparturesAreBookable() throws Exception {
        String token = bookingTrekker();
        String admin = adminToken();
        UUID draft = createDraft(admin, createTrack(admin, 1), guideUser(), today().plusDays(10), 100_000, 6);
        requestHold(token, draft, 1)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("DEPARTURE_NOT_FOUND"));
        requestHold(token, UUID.randomUUID(), 1).andExpect(status().isNotFound());

        UUID pastCutoff = publishedDeparture();
        jdbc.update("UPDATE departures SET start_date = ?, end_date = ? WHERE id = ?",
                today(), today().plusDays(1), pastCutoff);
        requestHold(token, pastCutoff, 1)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DEPARTURE_NOT_BOOKABLE"));

        UUID cancelled = publishedDeparture();
        authed(post("/api/admin/departures/" + cancelled + "/cancel"), admin, """
                {"reason_code":"WEATHER","reason_note":"Cyclone warning"}""").andExpect(status().isOk());
        requestHold(token, cancelled, 1)
                .andExpect(jsonPath("$.code").value("DEPARTURE_NOT_BOOKABLE"));
    }

    @Test
    void seatsNeverOversellAndOneLiveBookingPerTrekker() throws Exception {
        UUID departure = publishedDeparture(today().plusDays(30), 150_000, 3);
        String first = bookingTrekker();
        hold(first, departure, 2);

        requestHold(first, departure, 1)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ALREADY_BOOKED"))
                .andExpect(jsonPath("$.details.booking_id").exists());

        String second = bookingTrekker();
        requestHold(second, departure, 2)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("NOT_ENOUGH_SEATS"))
                .andExpect(jsonPath("$.details.seats_left").value(1));
        hold(second, departure, 1);

        assertThat(seatsTaken(departure)).isEqualTo(3);
        mockMvc.perform(get("/api/public/departures/" + departure))
                .andExpect(jsonPath("$.seats_left").value(0))
                .andExpect(jsonPath("$.bookable").value(false));
    }

    @Test
    void releasingAHoldFreesTheSeats() throws Exception {
        UUID departure = publishedDeparture();
        String token = bookingTrekker();
        UUID booking = hold(token, departure, 3);

        authed(delete(BOOKINGS + "/" + booking + "/hold"), token, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RELEASED"));
        assertThat(seatsTaken(departure)).isZero();
        authed(delete(BOOKINGS + "/" + booking + "/hold"), token, null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("BOOKING_NOT_HELD"));

        // A released booking doesn't block booking again.
        hold(token, departure, 1);
        authed(get(BOOKINGS), token, null).andExpect(jsonPath("$.items", hasSize(2)));
    }

    @Test
    void anotherTrekkersBookingIsNotFound() throws Exception {
        UUID departure = publishedDeparture();
        UUID booking = hold(bookingTrekker(), departure, 1);
        String other = bookingTrekker();

        for (MockHttpServletRequestBuilder request : new MockHttpServletRequestBuilder[]{
                get(BOOKINGS + "/" + booking), delete(BOOKINGS + "/" + booking + "/hold"),
                get(BOOKINGS + "/" + booking + "/cancellation-quote"), post(BOOKINGS + "/" + booking + "/cancel")}) {
            authed(request, other, null)
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("BOOKING_NOT_FOUND"));
        }
        authed(get(BOOKINGS), other, null).andExpect(jsonPath("$.items[*].id").isEmpty());
        assertThat(seatsTaken(departure)).isEqualTo(1);
    }

    @Test
    void guidesAndAdminsCannotBook() throws Exception {
        UUID departure = publishedDeparture();
        requestHold(adminToken(), departure, 1).andExpect(status().isForbidden());
        authed(get(BOOKINGS), adminToken(), null).andExpect(status().isForbidden());
        mockMvc.perform(get(BOOKINGS)).andExpect(status().isUnauthorized());
        assertThat(jdbc.queryForList("SELECT id FROM bookings WHERE departure_id = ?", departure)).isEmpty();
    }
}
