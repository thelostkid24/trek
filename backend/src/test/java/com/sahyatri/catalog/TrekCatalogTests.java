package com.sahyatri.catalog;

import com.sahyatri.auth.AuthTestSupport;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** docs/TRD.md §7.9. The database is shared across tests, so every check filters to this test's slugs. */
class TrekCatalogTests extends AuthTestSupport {

    private String slugOf(UUID trackId) {
        return jdbc.queryForObject("SELECT slug FROM tracks WHERE id = ?", String.class, trackId);
    }

    private static String trek(String slug) {
        return "$.items[?(@.slug == '" + slug + "')]";
    }

    @Test
    void tracksWithDatesAlwaysShowAndListedOnesShowWithout() throws Exception {
        String admin = adminToken();
        UUID guide = guideUser();

        UUID withDates = createTrack(admin, 2);
        UUID later = createDraft(admin, withDates, guide, today().plusDays(40), 249_900, 10);
        UUID sooner = createDraft(admin, withDates, guide, today().plusDays(20), 199_900, 8);
        UUID draftOnly = createDraft(admin, withDates, guide, today().plusDays(30), 199_900, 8);
        for (UUID id : new UUID[] {later, sooner}) {
            authed(post("/api/admin/departures/" + id + "/publish"), admin, null).andExpect(status().isOk());
        }
        UUID listedNoDates = createTrack(admin, 1);
        UUID hidden = createTrack(admin, 1);

        authed(put("/api/admin/tracks/" + listedNoDates + "/listed"), admin, "{\"listed\":true}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.listed").value(true));

        String dated = slugOf(withDates);
        mockMvc.perform(get("/api/public/tracks"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(trek(dated) + ".name", contains("Rajmachi Fort")))
                .andExpect(jsonPath(trek(dated) + ".cover_url", contains((Object) null)))
                .andExpect(jsonPath(trek(dated) + ".departures[*].id", contains(sooner.toString(), later.toString())))
                .andExpect(jsonPath(trek(dated) + ".departures[0].seats_left", contains(8)))
                .andExpect(jsonPath(trek(dated) + ".departures[0].guide.id", contains(guide.toString())))
                .andExpect(jsonPath(trek(slugOf(listedNoDates)) + ".departures", contains(empty())))
                .andExpect(jsonPath(trek(slugOf(hidden)), empty()))
                .andExpect(jsonPath("$.items[*].departures[*].id", not(hasItem(draftOnly.toString()))));

        // Unlisting only hides a track while it has no dates.
        authed(put("/api/admin/tracks/" + withDates + "/listed"), admin, "{\"listed\":false}").andExpect(status().isOk());
        authed(put("/api/admin/tracks/" + listedNoDates + "/listed"), admin, "{\"listed\":false}").andExpect(status().isOk());
        mockMvc.perform(get("/api/public/tracks"))
                .andExpect(jsonPath(trek(dated), hasSize(1)))
                .andExpect(jsonPath(trek(slugOf(listedNoDates)), empty()));

        Integer audits = jdbc.queryForObject(
                "SELECT count(*) FROM audit_events WHERE entity_id = ? AND action IN ('TRACK_LISTED','TRACK_UNLISTED')",
                Integer.class, listedNoDates);
        assertThat(audits).isEqualTo(2);
    }

    @Test
    void listingNeedsAnAdminAndAValue() throws Exception {
        String admin = adminToken();
        UUID track = createTrack(admin, 1);
        authed(put("/api/admin/tracks/" + track + "/listed"), admin, "{}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.fields.listed").exists());
        authed(put("/api/admin/tracks/" + UUID.randomUUID() + "/listed"), admin, "{\"listed\":true}")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TRACK_NOT_FOUND"));
        authed(put("/api/admin/tracks/" + track + "/listed"), emailTrekker(uniqueEmail()), "{\"listed\":true}")
                .andExpect(status().isForbidden());
    }
}
