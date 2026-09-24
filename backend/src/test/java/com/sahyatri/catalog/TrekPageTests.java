package com.sahyatri.catalog;

import com.jayway.jsonpath.JsonPath;
import com.sahyatri.auth.AuthTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TrekPageTests extends AuthTestSupport {

    private static String trekJson(String slug, String itinerary) {
        return """
                {"slug":"%s","name":"Kedarkantha","region":"Uttarakhand","difficulty":"MODERATE","duration_days":3,
                 "max_altitude_m":3810,"summary":"Snow summit above Sankri","description":"Pine and oak, then snow.",
                 "meeting_point":"Dehradun","distance_km":20.5,"base_altitude_m":1950,"highest_camp_m":3430,
                 "stay":"Tents · twin share","season_label":"Snow trek · Dec–Apr","itinerary":%s}"""
                .formatted(slug, itinerary);
    }

    private UUID createTrek(String admin, String slug) throws Exception {
        MvcResult result = authed(post("/api/admin/tracks"), admin, trekJson(slug, """
                [{"summary":"Dehradun to Sankri","end_altitude_m":1967},
                 {"summary":"Sankri to Juda ka Talab","distance_km":4,"start_altitude_m":1967,"end_altitude_m":2700,
                  "hours_min":4,"hours_max":5,"description":"Pine and oak most of the way."},
                 {"summary":"Summit, back to Sankri","start_altitude_m":2700,"high_altitude_m":3810,
                  "end_altitude_m":1967,"route_note":"long descent"}]"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.itinerary", hasSize(3)))
                .andExpect(jsonPath("$.distance_km").value(20.5))
                .andReturn();
        return UUID.fromString(JsonPath.read(result.getResponse().getContentAsString(), "$.id"));
    }

    private UUID publish(String admin, UUID track, UUID guide, int daysOut) throws Exception {
        UUID id = createDraft(admin, track, guide, today().plusDays(daysOut), 945_000, 10);
        authed(post("/api/admin/departures/" + id + "/publish"), admin, null).andExpect(status().isOk());
        return id;
    }

    @Test
    void oneTrekManyGuides() throws Exception {
        String admin = adminToken();
        String slug = uniqueSlug();
        UUID track = createTrek(admin, slug);
        UUID pratap = guideUser();
        UUID dinesh = guideUser();
        jdbc.update("UPDATE users SET full_name = 'Pratap Singh Rawat' WHERE id = ?", pratap);
        jdbc.update("""
                INSERT INTO trekker_profiles (user_id, home_city, bio, created_at, updated_at)
                VALUES (?, 'Sankri', 'Grew up under the ridge.', now(), now())""", pratap);

        UUID later = publish(admin, track, dinesh, 40);
        UUID sooner = publish(admin, track, pratap, 30);
        UUID done = publish(admin, track, pratap, 20);
        jdbc.update("UPDATE departures SET status = 'COMPLETED' WHERE id = ?", done);

        mockMvc.perform(get("/api/public/tracks/" + slug))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.track.name").value("Kedarkantha"))
                .andExpect(jsonPath("$.track.season_label").value("Snow trek · Dec–Apr"))
                .andExpect(jsonPath("$.track.highest_camp_m").value(3430))
                .andExpect(jsonPath("$.track.itinerary[*].day", contains(1, 2, 3)))
                .andExpect(jsonPath("$.track.itinerary[1].summary").value("Sankri to Juda ka Talab"))
                .andExpect(jsonPath("$.track.itinerary[1].distance_km").value(4))
                .andExpect(jsonPath("$.track.itinerary[1].hours_max").value(5))
                .andExpect(jsonPath("$.track.itinerary[1].description").value("Pine and oak most of the way."))
                .andExpect(jsonPath("$.track.itinerary[2].high_altitude_m").value(3810))
                .andExpect(jsonPath("$.track.itinerary[2].route_note").value("long descent"))
                .andExpect(jsonPath("$.departures[*].id", contains(sooner.toString(), later.toString())))
                .andExpect(jsonPath("$.departures[0].guide.id").value(pratap.toString()))
                .andExpect(jsonPath("$.departures[0].guide.home_city").value("Sankri"))
                .andExpect(jsonPath("$.departures[0].guide.led_this_trek").value(1))
                .andExpect(jsonPath("$.departures[0].seats_left").value(10))
                .andExpect(jsonPath("$.departures[1].guide.id").value(dinesh.toString()))
                .andExpect(jsonPath("$.departures[1].guide.led_this_trek").value(0));

        mockMvc.perform(get("/api/public/departures/" + sooner))
                .andExpect(jsonPath("$.guide.home_city").value("Sankri"))
                .andExpect(jsonPath("$.guide.led_this_trek").value(1))
                .andExpect(jsonPath("$.track.itinerary", hasSize(3)));

        mockMvc.perform(get("/api/public/guides/" + pratap))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.full_name").value("Pratap Singh Rawat"))
                .andExpect(jsonPath("$.home_city").value("Sankri"))
                .andExpect(jsonPath("$.bio").value("Grew up under the ridge."))
                .andExpect(jsonPath("$.treks_led").value(1))
                .andExpect(jsonPath("$.treks[0].track.slug").value(slug))
                .andExpect(jsonPath("$.treks[0].times").value(1))
                .andExpect(jsonPath("$.upcoming[*].id", contains(sooner.toString())));
    }

    private static String days(String... summaries) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < summaries.length; i++) {
            sb.append(i > 0 ? "," : "").append("{\"summary\":\"").append(summaries[i]).append("\"}");
        }
        return sb.append(']').toString();
    }

    @Test
    void unknownTrekAndNonGuidesAreNotFound() throws Exception {
        mockMvc.perform(get("/api/public/tracks/no-such-trek"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TRACK_NOT_FOUND"));
        String email = uniqueEmail();
        emailTrekker(email);
        mockMvc.perform(get("/api/public/guides/" + userIdByEmail(email)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("GUIDE_NOT_FOUND"));
        mockMvc.perform(get("/api/public/guides/" + UUID.randomUUID())).andExpect(status().isNotFound());
    }

    @Test
    void itineraryHasOneLinePerDayAndCanBeReplaced() throws Exception {
        String admin = adminToken();
        String slug = uniqueSlug();
        authed(post("/api/admin/tracks"), admin, trekJson(slug, "[{\"summary\":\"Only one day\"}]"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.fields.itinerary").exists());
        authed(post("/api/admin/tracks"), admin, trekJson(slug, days("Day one", " ", "Day three")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.fields['itinerary[1].summary']").exists());
        // Hours run min to max, and no day goes above the summit.
        authed(post("/api/admin/tracks"), admin, trekJson(slug, """
                [{"summary":"a"},{"summary":"b","hours_min":5,"hours_max":4},{"summary":"c"}]"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.fields['itinerary[1].hours_max']").exists());
        authed(post("/api/admin/tracks"), admin, trekJson(slug, """
                [{"summary":"a"},{"summary":"b"},{"summary":"c","high_altitude_m":4000}]"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.fields['itinerary[2].high_altitude_m']").exists());

        UUID track = createTrek(admin, slug);
        authed(put("/api/admin/tracks/" + track), admin, trekJson(slug, days("Day one, new", "Day two, new", "Day three, new")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.itinerary[*].summary", contains("Day one, new", "Day two, new", "Day three, new")));

        // Facts are optional; an empty itinerary clears it.
        authed(put("/api/admin/tracks/" + track), admin, trekJson(slug, "[]"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.itinerary", hasSize(0)));

        authed(put("/api/admin/tracks/" + track), admin, trekJson(slug, "[]")
                .replace("\"highest_camp_m\":3430", "\"highest_camp_m\":5000"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.fields.highest_camp_m").exists());
    }
}
