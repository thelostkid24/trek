package com.sahyatri.catalog;

import com.sahyatri.auth.AuthTestSupport;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TrekContentTests extends AuthTestSupport {

    private String slugOf(UUID track) {
        return jdbc.queryForObject("SELECT slug FROM tracks WHERE id = ?", String.class, track);
    }

    @Test
    void sharedListsComeFirstThenTheTreksOwn() throws Exception {
        String admin = adminToken();
        UUID track = createTrack(admin, 2);

        authed(put("/api/admin/content/FAQ"), admin, """
                {"items":[{"title":"Will there be snow?","body":"From late December."}]}""")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.FAQ[0].title").value("Will there be snow?"))
                .andExpect(jsonPath("$.INCLUDED").isArray());
        authed(put("/api/admin/tracks/" + track + "/content/FAQ"), admin, """
                {"items":[{"title":"  How do I get to Sankri?  ","body":" Overnight bus from Dehradun. "}]}""")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.FAQ", hasSize(1)))
                .andExpect(jsonPath("$.FAQ[0].body").value("Overnight bus from Dehradun."));
        authed(put("/api/admin/tracks/" + track + "/content/INCLUDED"), admin, """
                {"items":[{"body":"Guesthouse in Sankri"},{"body":"All meals"}]}""")
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/public/tracks/" + slugOf(track)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.FAQ[0].title").value("Will there be snow?"))
                .andExpect(jsonPath("$.content.FAQ[-1].title").value("How do I get to Sankri?"))
                .andExpect(jsonPath("$.content.INCLUDED[*].body", contains("Guesthouse in Sankri", "All meals")))
                .andExpect(jsonPath("$.content.WHY_US").isArray());

        // Replacing a list swaps it whole; an empty list clears it.
        authed(put("/api/admin/tracks/" + track + "/content/INCLUDED"), admin, """
                {"items":[{"body":"All meals"}]}""")
                .andExpect(jsonPath("$.INCLUDED[*].body", contains("All meals")));
        authed(put("/api/admin/tracks/" + track + "/content/INCLUDED"), admin, "{\"items\":[]}")
                .andExpect(jsonPath("$.INCLUDED", hasSize(0)));
        authed(get("/api/admin/tracks/" + track + "/content"), admin, null)
                .andExpect(jsonPath("$.FAQ", hasSize(1)));
    }

    @Test
    void listRulesAreChecked() throws Exception {
        String admin = adminToken();
        UUID track = createTrack(admin, 1);
        String base = "/api/admin/tracks/" + track + "/content/";

        authed(put(base + "FAQ"), admin, "{\"items\":[{\"body\":\"An answer with no question\"}]}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.fields['items[0].title']").exists());
        authed(put(base + "SAFETY"), admin, "{\"items\":[{\"badge\":\"1%\",\"body\":\"Walkie-talkies\"}]}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.fields['items[0].badge']").exists());
        authed(put(base + "WHY_US"), admin, """
                {"items":[{"badge":"1%","title":"1% to charity","body":"From every booking."}]}""")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.WHY_US[0].badge").value("1%"));
        authed(put(base + "NOPE"), admin, "{\"items\":[]}").andExpect(status().isNotFound());
        authed(put("/api/admin/tracks/" + UUID.randomUUID() + "/content/FAQ"), admin, "{\"items\":[]}")
                .andExpect(status().isNotFound());

        String trekker = emailTrekker(uniqueEmail());
        authed(put(base + "FAQ"), trekker, "{\"items\":[]}").andExpect(status().isForbidden());
    }

    @Test
    void servicesDifficultyRefundTiersAndCharityShowOnTheTrekPage() throws Exception {
        String admin = adminToken();
        String slug = uniqueSlug();
        String json = """
                {"slug":"%s","name":"Kedarkantha","region":"Uttarakhand","difficulty":"EASY_MODERATE",
                 "duration_days":5,"max_altitude_m":3810,"summary":"Snow summit","description":"Pine and oak.",
                 "meeting_point":"Sankri","pickup_drop":"Sankri to Sankri","cloakroom":true,"offloading":true,
                 "offloading_price_paise":%s}""";
        authed(post("/api/admin/tracks"), admin, json.formatted(slug, "null"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.difficulty").value("EASY_MODERATE"))
                .andExpect(jsonPath("$.offloading_price_paise").value(nullValue()));

        mockMvc.perform(get("/api/public/tracks/" + slug))
                .andExpect(jsonPath("$.track.pickup_drop").value("Sankri to Sankri"))
                .andExpect(jsonPath("$.track.cloakroom").value(true))
                .andExpect(jsonPath("$.track.offloading").value(true))
                .andExpect(jsonPath("$.snow_report").value(nullValue()))
                .andExpect(jsonPath("$.crowd", hasSize(0)))
                .andExpect(jsonPath("$.refund_tiers[*].min_days_before", contains(15, 7, 0)))
                .andExpect(jsonPath("$.refund_tiers[0].refund_bps").value(9000))
                // No charity name configured in tests, so the line is hidden.
                .andExpect(jsonPath("$.charity").value(nullValue()));
        mockMvc.perform(get("/api/public/departures?difficulty=easy_moderate")).andExpect(status().isOk());

        authed(post("/api/admin/tracks"), admin, json.formatted(uniqueSlug(), "50000")
                .replace("\"offloading\":true", "\"offloading\":false"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.fields.offloading_price_paise").exists());
    }

    @Test
    void guideCredentialsShowOnTheTrekPage() throws Exception {
        String admin = adminToken();
        UUID track = createTrack(admin, 2);
        UUID guide = guideUser();
        UUID id = createDraft(admin, track, guide, today().plusDays(30), 1_045_000, 10);
        authed(post("/api/admin/departures/" + id + "/publish"), admin, null).andExpect(status().isOk());

        authed(get("/api/admin/guides/" + guide + "/details"), admin, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.years_leading").value(nullValue()));
        authed(put("/api/admin/guides/" + guide + "/details"), admin, """
                {"leading_since":%d,"languages":"Hindi, Garhwali, English","certification":"NIM Basic",
                 "certification_number":"NIM-1234","quote":"The mountain sets the pace."}"""
                .formatted(today().getYear() - 6))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.years_leading").value(6));
        authed(put("/api/admin/guides/" + guide + "/details"), admin, """
                {"leading_since":%d}""".formatted(today().getYear() + 1))
                .andExpect(status().isBadRequest());
        authed(put("/api/admin/guides/" + userIdByEmail(emailTrekkerEmail()) + "/details"), admin, "{}")
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/public/tracks/" + slugOf(track)))
                .andExpect(jsonPath("$.departures[0].guide.years_leading").value(6))
                .andExpect(jsonPath("$.departures[0].guide.languages").value("Hindi, Garhwali, English"))
                .andExpect(jsonPath("$.departures[0].guide.certification_number").value("NIM-1234"))
                .andExpect(jsonPath("$.departures[0].guide.quote").value("The mountain sets the pace."))
                .andExpect(jsonPath("$.departures[0].guide.rating").value(nullValue()))
                .andExpect(jsonPath("$.departures[0].guide.review_count").value(0));
        mockMvc.perform(get("/api/public/guides/" + guide))
                .andExpect(jsonPath("$.certification").value("NIM Basic"))
                .andExpect(jsonPath("$.reviews", hasSize(0)));
    }

    private String emailTrekkerEmail() throws Exception {
        String email = uniqueEmail();
        emailTrekker(email);
        return email;
    }
}
