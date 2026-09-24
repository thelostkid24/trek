package com.sahyatri.review;

import com.sahyatri.auth.AuthTestSupport;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ReviewTests extends AuthTestSupport {

    @Test
    void trekkersReviewCompletedTreksAndGuidesGetARating() throws Exception {
        UUID departure = publishedDeparture();
        UUID guide = jdbc.queryForObject("SELECT guide_id FROM departures WHERE id = ?", UUID.class, departure);
        String asha = bookingTrekker();
        String ravi = bookingTrekker();
        UUID ashaBooking = confirmedBooking(asha, departure, 1);
        UUID raviBooking = confirmedBooking(ravi, departure, 2);
        String review = "/api/trekker/bookings/%s/review";

        // Not before the trek is done.
        authed(put(review.formatted(ashaBooking)), asha, "{\"rating\":5}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REVIEW_NOT_ALLOWED"));
        jdbc.update("UPDATE departures SET status = 'COMPLETED' WHERE id = ?", departure);

        authed(get(review.formatted(ashaBooking)), asha, null)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("REVIEW_NOT_FOUND"));
        authed(put(review.formatted(ashaBooking)), asha, "{\"rating\":4,\"body\":\"  Unhurried and kind.  \"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.body").value("Unhurried and kind."));
        // Rewriting keeps one review per booking.
        authed(put(review.formatted(ashaBooking)), asha, "{\"rating\":5,\"body\":\"Unhurried and kind.\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rating").value(5));
        authed(put(review.formatted(raviBooking)), ravi, "{\"rating\":4}").andExpect(status().isOk());

        // Someone else's booking, and bad ratings.
        authed(put(review.formatted(ashaBooking)), ravi, "{\"rating\":1}").andExpect(status().isNotFound());
        authed(put(review.formatted(raviBooking)), ravi, "{\"rating\":6}").andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/public/guides/" + guide))
                .andExpect(jsonPath("$.rating").value(4.5))
                .andExpect(jsonPath("$.review_count").value(2))
                .andExpect(jsonPath("$.reviews", hasSize(2)));
        mockMvc.perform(get("/api/public/guides/" + guide + "/reviews"))
                .andExpect(jsonPath("$.items[?(@.rating == 5)].body").value("Unhurried and kind."))
                .andExpect(jsonPath("$.items[0].trek_name").value("Rajmachi Fort"));
    }
}
