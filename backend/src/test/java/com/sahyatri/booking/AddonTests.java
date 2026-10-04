package com.sahyatri.booking;

import com.jayway.jsonpath.JsonPath;
import com.sahyatri.auth.AuthTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Insurance, offloading and transport per traveller (docs/TRD.md §7.6): chosen in "Who's coming?" after the hold,
 * priced from the trek, insurance compulsory where offered, locked once paid.
 */
class AddonTests extends AuthTestSupport {

    private static final String BOOKINGS = "/api/trekker/bookings/";

    @Test
    void travellersAddonsRepriceTheHold() throws Exception {
        UUID departure = publishedDeparture(today().plusDays(40), 219_900, 6);
        UUID track = trackOf(departure);
        offerAddons(track);
        mockMvc.perform(get("/api/public/departures/" + departure))
                .andExpect(jsonPath("$.track.insurance_price_paise").value(50_000))
                .andExpect(jsonPath("$.track.transport_price_paise").value(120_000));

        String token = bookingTrekker();
        UUID booking = holdWithoutTravellers(token, departure, 2);
        authed(get(BOOKINGS + booking), token, null).andExpect(jsonPath("$.amount_paise").value(439_800));

        // Both take our insurance, one takes offloading and transport.
        travellers(token, booking, traveller("A", "\"insurance\":true,\"offloading\":true,\"transport\":true"),
                traveller("B", "\"insurance\":true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.amount_paise").value(439_800 + 2 * 50_000 + 30_000 + 120_000))
                .andExpect(jsonPath("$.addons.insurance_seats").value(2))
                .andExpect(jsonPath("$.addons.offloading_seats").value(1))
                .andExpect(jsonPath("$.addons.transport_seats").value(1))
                .andExpect(jsonPath("$.travellers[0].offloading").value(true))
                .andExpect(jsonPath("$.travellers[1].transport").value(false));

        // B has their own policy instead: the price comes down.
        travellers(token, booking, traveller("A", "\"insurance\":true"),
                traveller("B", "\"insurance_id\":\"POL-778\""))
                .andExpect(jsonPath("$.amount_paise").value(439_800 + 50_000))
                .andExpect(jsonPath("$.travellers[1].insurance").value(false))
                .andExpect(jsonPath("$.travellers[1].insurance_id").value("POL-778"));
    }

    @Test
    void insuranceIsCompulsoryWhereOffered() throws Exception {
        UUID departure = publishedDeparture(today().plusDays(40), 219_900, 6);
        offerAddons(trackOf(departure));
        String token = bookingTrekker();
        UUID booking = holdWithoutTravellers(token, departure, 1);

        // No travellers yet, then one with no insurance at all: can't pay.
        orderRequest(token, booking).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TRAVELLERS_NEEDED"));
        travellers(token, booking, traveller("A", "\"offloading\":true")).andExpect(status().isOk());
        orderRequest(token, booking).andExpect(jsonPath("$.code").value("TRAVELLERS_NEEDED"));

        // Ours and theirs together is refused; their own policy alone is enough.
        travellers(token, booking, traveller("A", "\"insurance\":true,\"insurance_id\":\"POL-1\""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.fields['travellers[0].insurance_id']").exists());
        travellers(token, booking, traveller("A", "\"insurance_id\":\"POL-1\"")).andExpect(status().isOk());
        orderRequest(token, booking).andExpect(status().isCreated())
                .andExpect(jsonPath("$.amount_paise").value(219_900));
    }

    @Test
    void notOfferedAddonsAreRefused() throws Exception {
        UUID departure = publishedDeparture();
        String token = bookingTrekker();
        UUID booking = holdWithoutTravellers(token, departure, 1);
        travellers(token, booking, traveller("A", "\"transport\":true"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.fields['travellers[0].transport']").exists());
        // No insurance on offer: nothing to require before paying.
        orderRequest(token, booking).andExpect(status().isCreated());
    }

    @Test
    void anOrderForTheOldAmountIsReplaced() throws Exception {
        UUID departure = publishedDeparture(today().plusDays(40), 219_900, 6);
        offerAddons(trackOf(departure));
        String token = bookingTrekker();
        UUID booking = holdWithoutTravellers(token, departure, 1);
        travellers(token, booking, traveller("A", "\"insurance\":true")).andExpect(status().isOk());
        String[] first = order(token, booking);

        travellers(token, booking, traveller("A", "\"insurance\":true,\"transport\":true")).andExpect(status().isOk());
        String[] second = order(token, booking);
        orderRequest(token, booking).andExpect(jsonPath("$.amount_paise").value(219_900 + 50_000 + 120_000));
        assertThat(second[1]).isNotEqualTo(first[1]);
        assertThat(jdbc.queryForObject(
                "SELECT status FROM payments WHERE id = ?", String.class, UUID.fromString(first[0]))).isEqualTo("EXPIRED");
    }

    @Test
    void paidAddonsAreLockedAndRefundedAtTheTier() throws Exception {
        UUID departure = publishedDeparture(today().plusDays(20), 219_900, 6);
        offerAddons(trackOf(departure));
        String token = bookingTrekker();
        UUID booking = holdWithoutTravellers(token, departure, 2);
        travellers(token, booking, traveller("A", "\"insurance\":true,\"transport\":true"),
                traveller("B", "\"insurance\":true,\"transport\":true")).andExpect(status().isOk());
        String[] order = order(token, booking);
        verifyPayment(token, order[0], order[1], gateway.capture(order[1]).id()).andExpect(status().isOk());

        // Names can change after payment; what was paid for can't.
        travellers(token, booking, traveller("Asha", "\"insurance\":true,\"transport\":true"),
                traveller("B", "\"insurance\":true,\"transport\":true")).andExpect(status().isOk());
        travellers(token, booking, traveller("A", "\"insurance\":true"),
                traveller("B", "\"insurance\":true,\"transport\":true"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ADDONS_LOCKED"));

        // 20 days out is the 50% tier, and it covers the add-ons too.
        authed(get(BOOKINGS + booking + "/cancellation-quote"), token, null)
                .andExpect(jsonPath("$.refund_bps").value(5000))
                .andExpect(jsonPath("$.refund_paise").value((2 * 219_900 + 2 * 50_000 + 2 * 120_000) / 2));
    }

    private UUID holdWithoutTravellers(String token, UUID departure, int seats) throws Exception {
        String body = authed(post("/api/trekker/bookings"), token, """
                {"departure_id":"%s","seats":%d}""".formatted(departure, seats))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return UUID.fromString(JsonPath.read(body, "$.id"));
    }

    private static String traveller(String name, String addons) {
        return """
                {"full_name":"%s","date_of_birth":"1995-04-12","gender":"FEMALE"%s}"""
                .formatted(name, addons.isEmpty() ? "" : "," + addons);
    }

    private ResultActions travellers(String token, UUID booking, String... list)
            throws Exception {
        return authed(put(BOOKINGS + booking + "/travellers"), token,
                "{\"travellers\":[" + String.join(",", list) + "]}");
    }

    private ResultActions orderRequest(String token, UUID booking)
            throws Exception {
        return authed(post("/api/trekker/payments/orders"), token, """
                {"booking_id":"%s"}""".formatted(booking));
    }

    private UUID trackOf(UUID departure) {
        return jdbc.queryForObject("SELECT track_id FROM departures WHERE id = ?", UUID.class, departure);
    }

    private void offerAddons(UUID track) {
        jdbc.update("""
                UPDATE tracks SET insurance_price_paise = 50000, transport_price_paise = 120000,
                    offloading = true, offloading_price_paise = 30000 WHERE id = ?""", track);
    }
}
