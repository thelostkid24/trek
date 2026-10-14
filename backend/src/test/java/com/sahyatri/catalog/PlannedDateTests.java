package com.sahyatri.catalog;

import com.sahyatri.auth.AuthTestSupport;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.UUID;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Planned dates (docs/TRD.md §7.20): dates shown with "Guide: to be updated" before a guide is confirmed. Never
 * bookable; a published departure on the same start date takes the date's place on the trek page.
 */
class PlannedDateTests extends AuthTestSupport {

    private static String datesJson(LocalDate... starts) {
        StringBuilder items = new StringBuilder();
        for (int i = 0; i < starts.length; i++) {
            items.append(i == 0 ? "" : ",").append("{\"start_date\":\"").append(starts[i]).append('"')
                    .append(i == 0 ? ",\"note\":\" Christmas batch \"" : "").append('}');
        }
        return "{\"items\":[" + items + "]}";
    }

    @Test
    void plannedDatesShowUntilAPublishedDepartureTakesTheirPlace() throws Exception {
        String admin = adminToken();
        UUID track = createTrack(admin, 5);
        String slug = jdbc.queryForObject("SELECT slug FROM tracks WHERE id = ?", String.class, track);
        String path = "/api/admin/tracks/" + track + "/planned-dates";
        LocalDate a = today().plusDays(40);
        LocalDate b = today().plusDays(30);
        LocalDate c = today().plusDays(50);

        authed(put(path), admin, datesJson(a, b, c))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[*].start_date", contains(b.toString(), a.toString(), c.toString())))
                .andExpect(jsonPath("$.items[1].end_date").value(a.plusDays(4).toString()))
                .andExpect(jsonPath("$.items[1].note").value("Christmas batch"))
                .andExpect(jsonPath("$.items[0].note").doesNotExist());

        authed(put(path), admin, datesJson(today().minusDays(1)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.fields['items[0].start_date']").exists());
        authed(put(path), admin, datesJson(a, a))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.fields['items[1].start_date']").exists());
        authed(get(path), admin, null).andExpect(jsonPath("$.items", hasSize(3)));
        authed(put(path), bookingTrekker(), datesJson(a)).andExpect(status().isForbidden());

        UUID guide = guideUser();
        UUID departure = createDraft(admin, track, guide, b, 945_000, 10);
        authed(post("/api/admin/departures/" + departure + "/publish"), admin, null).andExpect(status().isOk());

        mockMvc.perform(get("/api/public/tracks/" + slug))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.departures[*].start_date", contains(b.toString())))
                .andExpect(jsonPath("$.planned_dates[*].start_date", contains(a.toString(), c.toString())))
                .andExpect(jsonPath("$.planned_dates[0].note").value("Christmas batch"));

        authed(put(path), admin, "{\"items\":[]}").andExpect(status().isOk()).andExpect(jsonPath("$.items", hasSize(0)));
        mockMvc.perform(get("/api/public/tracks/" + slug)).andExpect(jsonPath("$.planned_dates", hasSize(0)));
    }
}
