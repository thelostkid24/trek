package com.sahyatri.insights;

import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import com.sahyatri.auth.AuthTestSupport;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Admin Insights (§7.15). Every test class shares one database, so these compare before and after rather than
 * expecting absolute numbers.
 */
class InsightsTests extends AuthTestSupport {

    private static final String INSIGHTS = "/api/admin/insights";

    private DocumentContext insights(String admin) throws Exception {
        String body = authed(get(INSIGHTS), admin, null).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.parse(body);
    }

    private static long num(DocumentContext doc, String path) {
        return ((Number) doc.read(path)).longValue();
    }

    /** A field of the one list element whose {@code keyField} equals {@code key}; 0 when there is none. */
    private static long rowValue(DocumentContext doc, String list, String keyField, String key, String field) {
        List<Number> values = doc.read("$.%s[?(@.%s == '%s')].%s".formatted(list, keyField, key, field));
        return values.isEmpty() ? 0 : values.getFirst().longValue();
    }

    private static long funnel(DocumentContext doc, String key) {
        return rowValue(doc, "funnel", "key", key, "count");
    }

    @Test
    void countsAccountsBookingsSourcesAndTheFunnel() throws Exception {
        UUID departure = publishedDeparture();
        UUID guide = jdbc.queryForObject("SELECT guide_id FROM departures WHERE id = ?", UUID.class, departure);
        UUID track = jdbc.queryForObject("SELECT track_id FROM departures WHERE id = ?", UUID.class, departure);
        String admin = adminToken();
        String signupSource = "src-" + UUID.randomUUID().toString().substring(0, 8);
        String bookingSource = "src-" + UUID.randomUUID().toString().substring(0, 8);
        String campaign = "camp-" + UUID.randomUUID().toString().substring(0, 8);

        DocumentContext before = insights(admin);

        postJson("/api/auth/signup", """
                {"full_name":"Asha Rao","email":"%s","password":"trekking1","acquisition":{
                 "first_touch":{"utm_source":"%s","utm_campaign":"%s"},"device_type":"TABLET",
                 "heard_from":"BLOG_FORUM","marketing_email":true}}"""
                .formatted(uniqueEmail(), signupSource, campaign))
                .andExpect(status().isCreated());

        String trekker = bookingTrekker();
        UUID booking = UUID.fromString(JsonPath.read(authed(post("/api/trekker/bookings"), trekker, """
                {"departure_id":"%s","seats":2,"acquisition":{"last_touch":{"utm_source":"%s","utm_campaign":"%s"},
                 "device_type":"DESKTOP"}}""".formatted(departure, bookingSource, campaign))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id"));
        String[] order = order(trekker, booking);
        verifyPayment(trekker, order[0], order[1], gateway.capture(order[1]).id()).andExpect(status().isOk());

        DocumentContext after = insights(admin);

        assertThat(num(after, "$.days")).isEqualTo(30);
        assertThat(num(after, "$.headline.new_accounts") - num(before, "$.headline.new_accounts")).isEqualTo(2);
        assertThat(num(after, "$.headline.bookings_held") - num(before, "$.headline.bookings_held")).isEqualTo(1);
        assertThat(num(after, "$.headline.bookings_confirmed") - num(before, "$.headline.bookings_confirmed"))
                .isEqualTo(1);
        assertThat(num(after, "$.headline.gross_paise") - num(before, "$.headline.gross_paise")).isEqualTo(439_800);

        List<Object> daily = after.read("$.daily");
        assertThat(daily).hasSize(31);
        assertThat(num(after, "$.daily[-1].confirmed") - num(before, "$.daily[-1].confirmed")).isEqualTo(1);

        assertThat(funnel(after, "ACCOUNT") - funnel(before, "ACCOUNT")).isEqualTo(2);
        assertThat(funnel(after, "HELD") - funnel(before, "HELD")).isEqualTo(1);
        assertThat(funnel(after, "PAID") - funnel(before, "PAID")).isEqualTo(1);

        // Accounts by first touch, bookings and money by the booking's own last touch.
        assertThat(rowValue(after, "sources", "source", signupSource, "accounts")).isEqualTo(1);
        assertThat(rowValue(after, "sources", "source", signupSource, "confirmed_bookings")).isZero();
        assertThat(rowValue(after, "sources", "source", bookingSource, "accounts")).isZero();
        assertThat(rowValue(after, "sources", "source", bookingSource, "confirmed_bookings")).isEqualTo(1);
        assertThat(rowValue(after, "sources", "source", bookingSource, "gross_paise")).isEqualTo(439_800);
        assertThat(rowValue(after, "campaigns", "source", campaign, "accounts")).isEqualTo(1);
        assertThat(rowValue(after, "campaigns", "source", campaign, "confirmed_bookings")).isEqualTo(1);

        assertThat(rowValue(after, "heard_from", "key", "BLOG_FORUM", "count")
                - rowValue(before, "heard_from", "key", "BLOG_FORUM", "count")).isEqualTo(1);
        assertThat(rowValue(after, "devices", "key", "TABLET", "count")
                - rowValue(before, "devices", "key", "TABLET", "count")).isEqualTo(1);
        assertThat(rowValue(after, "signup_methods", "key", "EMAIL", "count")
                - rowValue(before, "signup_methods", "key", "EMAIL", "count")).isEqualTo(2);
        assertThat(num(after, "$.marketing_reach.email") - num(before, "$.marketing_reach.email")).isEqualTo(1);

        assertThat(num(after, "$.payments.paid") - num(before, "$.payments.paid")).isEqualTo(1);
        assertThat(rowValue(after, "treks", "track_id", track.toString(), "confirmed_bookings")).isEqualTo(1);
        assertThat(rowValue(after, "treks", "track_id", track.toString(), "seats")).isEqualTo(2);
        assertThat(rowValue(after, "upcoming", "departure_id", departure.toString(), "seats_taken")).isEqualTo(2);
        assertThat((List<Object>) after.read("$.guides[?(@.guide_id == '%s')]".formatted(guide))).hasSize(1);
    }

    @Test
    void accountsMadeBeforeTrackingAreUnknownAndTheWindowIsChecked() throws Exception {
        String admin = adminToken();
        DocumentContext before = insights(admin);
        emailTrekker(uniqueEmail());
        DocumentContext after = insights(admin);
        assertThat(rowValue(after, "sources", "source", "unknown", "accounts")
                - rowValue(before, "sources", "source", "unknown", "accounts")).isEqualTo(1);

        authed(get(INSIGHTS).param("days", "7"), admin, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.days").value(7))
                .andExpect(jsonPath("$.daily.length()").value(8));
        authed(get(INSIGHTS).param("days", "0"), admin, null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        authed(get(INSIGHTS).param("days", "366"), admin, null).andExpect(status().isBadRequest());
    }

    @Test
    void onlyAdminsSeeInsights() throws Exception {
        authed(get(INSIGHTS), emailTrekker(uniqueEmail()), null).andExpect(status().isForbidden());
        mockMvc.perform(get(INSIGHTS)).andExpect(status().isUnauthorized());
    }
}
