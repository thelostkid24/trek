package com.sahyatri.catalog;

import com.jayway.jsonpath.JsonPath;
import com.sahyatri.auth.AuthTestSupport;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.nullValue;
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
        authed(put("/api/admin/guides/" + veteran + "/record"), admin, """
                {"routes":[{"track_id":"%s","times_led":3},{"track_id":"%s","times_led":9}]}""".formatted(track, other))
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
    void routesOffTheCatalogCountAndCertifiedFollowsVerifiedCertificates() throws Exception {
        String admin = adminToken();
        UUID kedarkantha = createTrack(admin, 2);
        UUID guideId = guideUser();
        String kedarkanthaName = jdbc.queryForObject("SELECT name FROM tracks WHERE id = ?", String.class, kedarkantha);
        UUID done = publish(admin, kedarkantha, guideId, 20);
        jdbc.update("UPDATE departures SET status = 'COMPLETED' WHERE id = ?", done);
        authed(put("/api/admin/guides/" + guideId + "/record"), admin, """
                {"routes":[{"track_id":"%s","times_led":50},{"name":"Har Ki Dun","times_led":30},{"name":"Yunam"}]}"""
                .formatted(kedarkantha)).andExpect(status().isOk());
        // Course columns alone (§7.12) no longer make a guide "Certified".
        authed(put("/api/admin/guides/" + guideId + "/details"), admin, """
                {"bmc_institute":"NIM","bmc_certificate_number":"B-1"}""").andExpect(status().isOk());

        mockMvc.perform(get("/api/public/guides"))
                .andExpect(jsonPath(guide(guideId) + ".treks_led", contains(81)))
                .andExpect(jsonPath(guide(guideId) + ".routes", contains(List.of(kedarkanthaName, "Har Ki Dun", "Yunam"))))
                .andExpect(jsonPath(guide(guideId) + ".certified", contains(false)));
        mockMvc.perform(get("/api/public/guides/" + guideId))
                .andExpect(jsonPath("$.treks_led").value(81))
                .andExpect(jsonPath("$.certified").value(false));

        UUID cert = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO guide_certifications (id, guide_id, type, issuer, verified_at, created_at, updated_at)
                VALUES (?, ?, 'BMC', 'NIM, Uttarkashi', now(), now(), now())""", cert, guideId);
        UUID upcoming = publish(admin, kedarkantha, guideId, 30);
        mockMvc.perform(get("/api/public/guides"))
                .andExpect(jsonPath(guide(guideId) + ".certified", contains(true)));
        mockMvc.perform(get("/api/public/guides/" + guideId)).andExpect(jsonPath("$.certified").value(true));
        mockMvc.perform(get("/api/public/departures/" + upcoming)).andExpect(jsonPath("$.guide.certified").value(true));

        // Expired, or unverified, it's gone everywhere.
        jdbc.update("UPDATE guide_certifications SET valid_to = ? WHERE id = ?", today().minusDays(1), cert);
        mockMvc.perform(get("/api/public/guides"))
                .andExpect(jsonPath(guide(guideId) + ".certified", contains(false)));
        jdbc.update("UPDATE guide_certifications SET valid_to = NULL, verified_at = NULL WHERE id = ?", cert);
        mockMvc.perform(get("/api/public/departures/" + upcoming)).andExpect(jsonPath("$.guide.certified").value(false));
    }

    @Test
    void anAdminMarksAGuideRouteVerified() throws Exception {
        String admin = adminToken();
        UUID guideId = guideUser();
        mockMvc.perform(get("/api/public/guides"))
                .andExpect(jsonPath(guide(guideId) + ".route_verified", contains(false)));

        authed(put("/api/admin/guides/" + guideId + "/details"), admin, """
                {"languages":"Hindi","route_verified":true}""")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.route_verified").value(true));
        mockMvc.perform(get("/api/public/guides"))
                .andExpect(jsonPath(guide(guideId) + ".route_verified", contains(true)));

        // A save that leaves it out turns it off, like every other field.
        authed(put("/api/admin/guides/" + guideId + "/details"), admin, """
                {"languages":"Hindi"}""").andExpect(jsonPath("$.route_verified").value(false));
    }

    @Test
    void anAdminSetsAGuidesBatchesLed() throws Exception {
        String admin = adminToken();
        UUID track = createTrack(admin, 2);
        UUID guideId = guideUser();
        authed(put("/api/admin/guides/" + guideId + "/record"), admin, """
                {"routes":[{"track_id":"%s","times_led":40}]}""".formatted(track)).andExpect(status().isOk());
        mockMvc.perform(get("/api/public/guides"))
                .andExpect(jsonPath(guide(guideId) + ".treks_led", contains(40)));

        authed(put("/api/admin/guides/" + guideId + "/details"), admin, """
                {"batches_led":12}""")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.batches_led").value(12));
        mockMvc.perform(get("/api/public/guides"))
                .andExpect(jsonPath(guide(guideId) + ".treks_led", contains(12)));
        mockMvc.perform(get("/api/public/guides/" + guideId)).andExpect(jsonPath("$.treks_led").value(12));

        // Cleared, it's worked out from their routes again.
        authed(put("/api/admin/guides/" + guideId + "/details"), admin, "{}")
                .andExpect(jsonPath("$.batches_led").value(nullValue()));
        mockMvc.perform(get("/api/public/guides"))
                .andExpect(jsonPath(guide(guideId) + ".treks_led", contains(40)));
        authed(put("/api/admin/guides/" + guideId + "/details"), admin, """
                {"batches_led":-1}""").andExpect(status().isBadRequest());
    }

    @Test
    void aTreksStateShowsOnTheGuideList() throws Exception {
        String admin = adminToken();
        UUID track = createTrack(admin, 2);
        UUID guideId = guideUser();
        String slug = jdbc.queryForObject("SELECT slug FROM tracks WHERE id = ?", String.class, track);
        authed(put("/api/admin/guides/" + guideId + "/record"), admin, """
                {"routes":[{"track_id":"%s","times_led":4}]}""".formatted(track)).andExpect(status().isOk());
        mockMvc.perform(get("/api/public/guides"))
                .andExpect(jsonPath(guide(guideId) + ".treks[0].state", contains((Object) null)));

        authed(put("/api/admin/tracks/" + track), admin, trackJson(slug, "\"state\":\" Uttarakhand \","))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("Uttarakhand"));
        mockMvc.perform(get("/api/public/guides"))
                .andExpect(jsonPath(guide(guideId) + ".treks[0].state", contains("Uttarakhand")));

        authed(put("/api/admin/tracks/" + track), admin, trackJson(slug, "\"state\":\"" + "x".repeat(61) + "\","))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.fields.state").exists());
        authed(put("/api/admin/tracks/" + track), admin, trackJson(slug, ""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").doesNotExist());
    }

    private static String trackJson(String slug, String state) {
        return """
                {"slug":"%s","name":"Rajmachi Fort","region":"Lonavala",%s"difficulty":"EASY","duration_days":2,
                 "summary":"s","description":"d","meeting_point":"m"}""".formatted(slug, state);
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
