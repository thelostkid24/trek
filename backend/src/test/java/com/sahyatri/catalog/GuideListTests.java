package com.sahyatri.catalog;

import com.jayway.jsonpath.JsonPath;
import com.sahyatri.auth.AuthTestSupport;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** docs/TRD.md §7.17. The database is shared across tests, so every check filters to this test's guides. */
class GuideListTests extends AuthTestSupport {

    private static String guide(UUID id) {
        return "$.items[?(@.id == '" + id + "')]";
    }

    private UUID publish(String admin, UUID track, UUID guide, int daysOut) throws Exception {
        UUID id = createDraft(admin, track, guide, today().plusDays(daysOut), 945_000, 10);
        authed(post("/api/admin/departures/" + id + "/publish"), admin, null).andExpect(status().isOk());
        return id;
    }

    @Test
    void everyActiveGuideWithTheirRecord() throws Exception {
        String admin = adminToken();
        UUID track = createTrack(admin, 2);
        UUID other = createTrack(admin, 2);
        UUID leading = guideUser();
        UUID veteran = guideUser();
        UUID newcomer = guideUser();
        UUID gone = guideUser();
        jdbc.update("UPDATE users SET full_name = 'Pratap Singh Rawat' WHERE id = ?", leading);
        jdbc.update("""
                INSERT INTO trekker_profiles (user_id, home_city, bio, created_at, updated_at)
                VALUES (?, 'Sankri', 'Grew up under the ridge.', now(), now())""", leading);
        jdbc.update("UPDATE users SET status = 'DISABLED' WHERE id = ?", gone);

        UUID done = publish(admin, track, leading, 20);
        jdbc.update("UPDATE departures SET status = 'COMPLETED' WHERE id = ?", done);
        publish(admin, track, leading, 30);
        publish(admin, other, leading, 40);
        authed(put("/api/admin/guides/" + leading + "/details"), admin, """
                {"languages":"Hindi, Garhwali","bmc_institute":"NIM","bmc_certificate_number":"B-1"}""")
                .andExpect(status().isOk());
        authed(put("/api/admin/guides/" + veteran + "/details"), admin, """
                {"prior_treks":[{"track_id":"%s","times":3},{"track_id":"%s","times":9}]}""".formatted(track, other))
                .andExpect(status().isOk());

        String body = mockMvc.perform(get("/api/public/guides"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(guide(leading) + ".full_name", contains("Pratap Singh Rawat")))
                .andExpect(jsonPath(guide(leading) + ".home_city", contains("Sankri")))
                .andExpect(jsonPath(guide(leading) + ".languages", contains("Hindi, Garhwali")))
                .andExpect(jsonPath(guide(leading) + ".bmc_institute", contains("NIM")))
                .andExpect(jsonPath(guide(leading) + ".treks_led", contains(1)))
                .andExpect(jsonPath(guide(leading) + ".upcoming", contains(2)))
                .andExpect(jsonPath(guide(leading) + ".review_count", contains(0)))
                .andExpect(jsonPath(guide(veteran) + ".treks_led", contains(12)))
                .andExpect(jsonPath(guide(veteran) + ".treks[*].slug", contains(
                        jdbc.queryForObject("SELECT slug FROM tracks WHERE id = ?", String.class, other),
                        jdbc.queryForObject("SELECT slug FROM tracks WHERE id = ?", String.class, track))))
                .andExpect(jsonPath(guide(veteran) + ".upcoming", contains(0)))
                .andExpect(jsonPath(guide(newcomer) + ".treks_led", contains(0)))
                .andExpect(jsonPath(guide(newcomer) + ".rating", contains((Object) null)))
                .andExpect(jsonPath(guide(gone), empty()))
                .andReturn().getResponse().getContentAsString();

        // Guides leading upcoming dates come first, then the most treks led.
        List<String> ids = JsonPath.read(body, "$.items[*].id");
        assertThat(ids.indexOf(leading.toString())).isLessThan(ids.indexOf(veteran.toString()));
        assertThat(ids.indexOf(veteran.toString())).isLessThan(ids.indexOf(newcomer.toString()));
    }

    @Test
    void trekkersAreNotGuides() throws Exception {
        String email = uniqueEmail();
        emailTrekker(email);
        mockMvc.perform(get("/api/public/guides"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(guide(userIdByEmail(email)), empty()));
    }
}
