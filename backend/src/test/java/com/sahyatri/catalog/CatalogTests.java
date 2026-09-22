package com.sahyatri.catalog;

import com.sahyatri.auth.AuthTestSupport;
import com.sahyatri.catalog.service.DepartureLifecycleJob;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CatalogTests extends AuthTestSupport {

    private static final String PUBLIC = "/api/public/departures";
    private static final String ADMIN = "/api/admin/departures";

    @Autowired
    DepartureLifecycleJob lifecycleJob;

    @Test
    void draftIsHiddenUntilPublishedAndPublishFreezesGuideShare() throws Exception {
        String admin = adminToken();
        UUID track = createTrack(admin, 2);
        UUID guide = guideUser();
        UUID id = createDraft(admin, track, guide, today().plusDays(20), 219_900, 6);

        mockMvc.perform(get(PUBLIC + "/" + id)).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("DEPARTURE_NOT_FOUND"));
        mockMvc.perform(get(PUBLIC)).andExpect(jsonPath("$.items[*].id", not(hasItem(id.toString()))));

        authed(post(ADMIN + "/" + id + "/publish"), admin, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PUBLISHED"))
                .andExpect(jsonPath("$.guide_share_bps").value(7000))
                .andExpect(jsonPath("$.end_date").value(today().plusDays(21).toString()));

        mockMvc.perform(get(PUBLIC)).andExpect(jsonPath("$.items[*].id", hasItem(id.toString())));
        mockMvc.perform(get(PUBLIC + "/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.track.name").value("Rajmachi Fort"))
                .andExpect(jsonPath("$.track.meeting_point").value("Lonavala station"))
                .andExpect(jsonPath("$.guide.id").value(guide.toString()))
                .andExpect(jsonPath("$.seats_left").value(6))
                .andExpect(jsonPath("$.bookable").value(true))
                .andExpect(jsonPath("$.guide.email").doesNotExist());

        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_events WHERE entity_id = ? AND action = ?",
                Integer.class, id, "DEPARTURE_PUBLISHED")).isEqualTo(1);

        // Published departures are no longer editable.
        authed(put(ADMIN + "/" + id), admin, departureJson(track, guide, today().plusDays(25), 6))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DEPARTURE_NOT_DRAFT"));
        authed(post(ADMIN + "/" + id + "/publish"), admin, null)
                .andExpect(jsonPath("$.code").value("DEPARTURE_NOT_DRAFT"));
    }

    @Test
    void publicListFiltersByMonthAndDifficulty() throws Exception {
        var start = today().plusMonths(3).withDayOfMonth(10);
        UUID id = publishedDeparture(start, 150_000, 6);
        String month = start.toString().substring(0, 7);

        mockMvc.perform(get(PUBLIC).param("month", month))
                .andExpect(jsonPath("$.items[*].id", hasItem(id.toString())));
        mockMvc.perform(get(PUBLIC).param("month", start.plusMonths(1).toString().substring(0, 7)))
                .andExpect(jsonPath("$.items[*].id", not(hasItem(id.toString()))));
        mockMvc.perform(get(PUBLIC).param("difficulty", "easy"))
                .andExpect(jsonPath("$.items[*].id", hasItem(id.toString())));
        mockMvc.perform(get(PUBLIC).param("difficulty", "CHALLENGING"))
                .andExpect(jsonPath("$.items[*].id", not(hasItem(id.toString()))));

        mockMvc.perform(get(PUBLIC).param("month", "October"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.fields.month").exists());
        mockMvc.perform(get(PUBLIC).param("difficulty", "EXTREME"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.fields.difficulty").exists());
        mockMvc.perform(get(PUBLIC + "/not-a-uuid")).andExpect(status().isNotFound());
    }

    @Test
    void departureValidationAndGuideRule() throws Exception {
        String admin = adminToken();
        UUID track = createTrack(admin, 1);
        UUID guide = guideUser();

        authed(post(ADMIN), admin, departureJson(track, guide, today().plusDays(10), 11))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.fields.max_group_size").exists());
        authed(post(ADMIN), admin, departureJson(track, guide, today().minusDays(1), 6))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.fields.start_date").exists());

        String trekkerEmail = uniqueEmail();
        emailTrekker(trekkerEmail);
        UUID trekker = jdbc.queryForObject("SELECT id FROM users WHERE email = ?", UUID.class, trekkerEmail);
        authed(post(ADMIN), admin, departureJson(track, trekker, today().plusDays(10), 6))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("NOT_A_GUIDE"));
        authed(post(ADMIN), admin, departureJson(UUID.randomUUID(), guide, today().plusDays(10), 6))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TRACK_NOT_FOUND"));
    }

    @Test
    void publishNeedsStartDateBeyondCutoff() throws Exception {
        String admin = adminToken();
        UUID id = createDraft(admin, createTrack(admin, 1), guideUser(), today(), 100_000, 4);

        authed(post(ADMIN + "/" + id + "/publish"), admin, null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("START_DATE_TOO_SOON"));

        authed(delete(ADMIN + "/" + id), admin, null).andExpect(status().isNoContent());
        authed(get(ADMIN), admin, null).andExpect(jsonPath("$.items[*].id", not(hasItem(id.toString()))));
    }

    @Test
    void cancelRequiresReasonAndPublishedStatus() throws Exception {
        String admin = adminToken();
        UUID id = publishedDeparture();

        authed(post(ADMIN + "/" + id + "/cancel"), admin, """
                {"reason_code":"LOW_FILL","reason_note":"only two people"}""")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.fields.reason_code").exists());
        authed(post(ADMIN + "/" + id + "/cancel"), admin, """
                {"reason_code":"WEATHER","reason_note":"  "}""")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.fields.reason_note").exists());

        authed(post(ADMIN + "/" + id + "/cancel"), admin, """
                {"reason_code":"WEATHER","reason_note":" Red alert for the ghats "}""")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.cancel_reason_note").value("Red alert for the ghats"));

        mockMvc.perform(get(PUBLIC + "/" + id))
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.bookable").value(false));
        authed(post(ADMIN + "/" + id + "/cancel"), admin, """
                {"reason_code":"SAFETY","reason_note":"again"}""")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DEPARTURE_NOT_CANCELLABLE"));
    }

    @Test
    void lifecycleJobExpiresUnsoldAndCompletesFinished() throws Exception {
        UUID unsold = publishedDeparture();
        UUID sold = publishedDeparture();
        UUID ended = publishedDeparture();
        jdbc.update("UPDATE departures SET start_date = ?, end_date = ? WHERE id = ?",
                today(), today().plusDays(1), unsold);
        jdbc.update("UPDATE departures SET start_date = ?, end_date = ?, seats_taken = 2 WHERE id = ?",
                today(), today().plusDays(1), sold);
        jdbc.update("UPDATE departures SET start_date = ?, end_date = ?, seats_taken = 1 WHERE id = ?",
                today().minusDays(3), today().minusDays(2), ended);

        lifecycleJob.run();

        assertThat(statusOf(unsold)).isEqualTo("EXPIRED");
        assertThat(statusOf(sold)).isEqualTo("PUBLISHED");
        assertThat(statusOf(ended)).isEqualTo("COMPLETED");
    }

    @Test
    void trackSlugIsUniqueAndDurationLocksOnceUsed() throws Exception {
        String admin = adminToken();
        UUID track = createTrack(admin, 2);
        String slug = jdbc.queryForObject("SELECT slug FROM tracks WHERE id = ?", String.class, track);

        authed(post("/api/admin/tracks"), admin, trackJson(slug, 2))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SLUG_TAKEN"));
        authed(put("/api/admin/tracks/" + track), admin, trackJson("Bad Slug", 2))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.fields.slug").exists());

        createDraft(admin, track, guideUser(), today().plusDays(10), 100_000, 6);
        authed(put("/api/admin/tracks/" + track), admin, trackJson(slug, 3))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TRACK_IN_USE"));
        authed(put("/api/admin/tracks/" + track), admin, trackJson(slug, 2))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Renamed"));
    }

    @Test
    void adminRoutesNeedAdminRole() throws Exception {
        String trekker = emailTrekker(uniqueEmail());
        authed(get(ADMIN), trekker, null)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        mockMvc.perform(get("/api/admin/tracks")).andExpect(status().isUnauthorized());
    }

    private String statusOf(UUID id) {
        return jdbc.queryForObject("SELECT status FROM departures WHERE id = ?", String.class, id);
    }

    private static String departureJson(UUID track, UUID guide, java.time.LocalDate start, int maxGroupSize) {
        return """
                {"track_id":"%s","guide_id":"%s","start_date":"%s","price_paise":150000,"max_group_size":%d}"""
                .formatted(track, guide, start, maxGroupSize);
    }

    private static String trackJson(String slug, int durationDays) {
        return """
                {"slug":"%s","name":" Renamed ","region":"Lonavala","difficulty":"MODERATE","duration_days":%d,
                 "summary":"s","description":"d","meeting_point":"m"}""".formatted(slug, durationDays);
    }
}
