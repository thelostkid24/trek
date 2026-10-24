package com.sahyatri.guide;

import com.jayway.jsonpath.JsonPath;
import com.sahyatri.auth.AuthTestSupport;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** A guide's record kept by admins: routes, pledges, picked stats and treks in the last three years (§7.21.8). */
class GuideRecordTests extends AuthTestSupport {

    @Test
    void adminKeepsTheRecordAndThePageWorksOutTheStats() throws Exception {
        String adminEmail = uniqueEmail();
        String admin = tokenWithRole(adminEmail, "ADMIN");
        String email = uniqueEmail();
        String guide = tokenWithRole(email, "GUIDE");
        UUID guideId = userIdByEmail(email);
        UUID kedarkantha = createTrack(admin, 2);
        authed(put("/api/admin/guides/" + guideId + "/details"), admin, """
                {"leading_since":%d}""".formatted(today().getYear() - 4)).andExpect(status().isOk());
        String amc = JsonPath.read(authed(post("/api/guide/certifications"), guide, """
                {"type":"AMC","issuer":"ABVIMAS, Manali","year":2026,"number":"76"}""")
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");
        authed(post("/api/guide/certifications"), guide, """
                {"type":"BMC","issuer":"Nehru Institute of Mountaineering, Uttarkashi","year":2025}""")
                .andExpect(status().isCreated());
        authed(post("/api/guide/certifications"), guide, """
                {"type":"FIRST_AID","issuer":"Within the basic and advanced courses","in_course":true}""")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("IN_COURSE"));
        authed(post("/api/admin/guides/" + guideId + "/certifications/" + amc + "/verify"), admin, null)
                .andExpect(status().isOk());

        authed(put("/api/admin/guides/" + guideId + "/record"), admin, """
                {"treks_last_3y":21,
                 "routes":[{"track_id":"%s","times_led":4,"last_led_year":2025,"availability":"FROM_DATE",
                            "available_from":"%s"},
                           {"name":"Yunam","kind":"PEAK","most_recent":true},
                           {"name":"Friendship Peak","kind":"PEAK","last_led_year":2026,"last_led_month":9,
                            "availability":"ANY_TIME","season_from_month":3,"season_to_month":10}],
                 "pledges":["NO_SERIOUS_INCIDENTS","NO_ALCOHOL"],
                 "stats":[{"key":"RATING"},{"key":"TREKS_LAST_3Y"},{"key":"YEARS_LEADING"},{"key":"ROUTES_LED"},
                          {"key":"PEAK_CLIMBS"},{"key":"EVACUATIONS"},{"key":"ROUTE_TIMES","route_index":0}]}"""
                .formatted(kedarkantha, today().plusDays(20)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.treks_last_3y").value(21))
                .andExpect(jsonPath("$.routes", hasSize(3)))
                .andExpect(jsonPath("$.routes[0].track_name").isString())
                .andExpect(jsonPath("$.pledges", contains("NO_ALCOHOL", "NO_SERIOUS_INCIDENTS")))
                .andExpect(jsonPath("$.stats[6].route_index").value(0));
        Map<String, Object> event = jdbc.queryForMap("""
                SELECT actor_id, data::text AS data FROM audit_events
                WHERE action = 'GUIDE_PLEDGES_RECORDED' AND entity_id = ?""", guideId);
        assertThat(event.get("actor_id")).isEqualTo(userIdByEmail(adminEmail));
        assertThat((String) event.get("data")).contains("NO_ALCOHOL");

        mockMvc.perform(get("/api/public/guides/" + guideId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.treks[*].name", contains(trackName(kedarkantha), "Yunam", "Friendship Peak")))
                .andExpect(jsonPath("$.treks[0].times").value(4))
                .andExpect(jsonPath("$.treks[1].track").value(nullValue()))
                .andExpect(jsonPath("$.treks[1].times").value(nullValue()))
                .andExpect(jsonPath("$.treks[1].most_recent").value(true))
                .andExpect(jsonPath("$.treks[2].season_from_month").value(3))
                .andExpect(jsonPath("$.stats[*].value", contains("New", "21", "4 yrs", "3", "2", "0", "4×")))
                .andExpect(jsonPath("$.stats[4].label").value("peak climbs led (Yunam, Friendship)"))
                .andExpect(jsonPath("$.stats[6].brief").value(trackName(kedarkantha) + " 4×"))
                .andExpect(jsonPath("$.trained_at", contains("NIM", "ABVIMAS")));
        mockMvc.perform(get("/api/public/guides/" + guideId + "/credentials"))
                .andExpect(jsonPath("$.items[*].status", contains("PENDING", "VERIFIED", "IN_COURSE")))
                .andExpect(jsonPath("$.pledges", contains("NO_ALCOHOL", "NO_SERIOUS_INCIDENTS")))
                .andExpect(jsonPath("$.verified").value(true));

        // Keeping a route by id keeps its stat; dropping the route drops the stat. Saving the same pledges logs nothing.
        String record = authed(get("/api/admin/guides/" + guideId + "/record"), admin, null)
                .andReturn().getResponse().getContentAsString();
        List<String> ids = JsonPath.read(record, "$.routes[*].id");
        authed(put("/api/admin/guides/" + guideId + "/record"), admin, """
                {"routes":[{"id":"%s","name":"Yunam","kind":"PEAK"}],"pledges":["NO_ALCOHOL","NO_SERIOUS_INCIDENTS"],
                 "stats":[{"key":"ROUTES_LED"},{"key":"COURSES"},{"key":"TOP_COURSE"}]}""".formatted(ids.get(1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.routes[0].id").value(ids.get(1)))
                .andExpect(jsonPath("$.treks_last_3y").value(nullValue()));
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM audit_events WHERE action = 'GUIDE_PLEDGES_RECORDED' AND entity_id = ?""",
                Integer.class, guideId)).isEqualTo(1);
        mockMvc.perform(get("/api/public/guides/" + guideId))
                .andExpect(jsonPath("$.stats[*].value", contains("1", "2", "AMC")))
                .andExpect(jsonPath("$.stats[1].label").value("mountaineering courses (NIM, ABVIMAS)"))
                .andExpect(jsonPath("$.stats[2].label").value("Advanced Mountaineering Course (ABVIMAS)"));

        // No picks: the page shows its default row.
        authed(put("/api/admin/guides/" + guideId + "/record"), admin, "{}").andExpect(status().isOk());
        mockMvc.perform(get("/api/public/guides/" + guideId)).andExpect(jsonPath("$.stats").value(nullValue()));
    }

    @Test
    void changingPledgesKeepsTheOnesLeftIn() throws Exception {
        String firstEmail = uniqueEmail();
        String first = tokenWithRole(firstEmail, "ADMIN");
        String second = tokenWithRole(uniqueEmail(), "ADMIN");
        UUID guideId = guideUser();
        String path = "/api/admin/guides/" + guideId + "/record";

        authed(put(path), first, """
                {"pledges":["NO_ALCOHOL"]}""").andExpect(status().isOk());
        // Another admin adds the second pledge: the first stays, still recorded by whoever recorded it.
        authed(put(path), second, """
                {"pledges":["NO_ALCOHOL","NO_SERIOUS_INCIDENTS"]}""")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pledges", contains("NO_ALCOHOL", "NO_SERIOUS_INCIDENTS")));
        assertThat(jdbc.queryForObject("""
                SELECT recorded_by FROM guide_pledges WHERE guide_id = ? AND kind = 'NO_ALCOHOL'""",
                UUID.class, guideId)).isEqualTo(userIdByEmail(firstEmail));

        authed(put(path), second, """
                {"pledges":["NO_SERIOUS_INCIDENTS"]}""")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pledges", contains("NO_SERIOUS_INCIDENTS")));
        mockMvc.perform(get("/api/public/guides/" + guideId + "/credentials"))
                .andExpect(jsonPath("$.pledges", contains("NO_SERIOUS_INCIDENTS")));
    }

    @Test
    void recordIsChecked() throws Exception {
        String admin = adminToken();
        UUID guideId = guideUser();
        UUID track = createTrack(admin, 2);
        String path = "/api/admin/guides/" + guideId + "/record";

        authed(put(path), admin, """
                {"routes":[{"track_id":"%s","name":"Both"}]}""".formatted(track))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.fields['routes[0].name']").exists());
        authed(put(path), admin, """
                {"routes":[{"name":"Rani Sui","last_led_month":9}]}""")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.fields['routes[0].last_led_year']").exists());
        authed(put(path), admin, """
                {"routes":[{"name":"Rani Sui","availability":"FROM_DATE"}]}""")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.fields['routes[0].available_from']").exists());
        authed(put(path), admin, """
                {"routes":[{"id":"%s","name":"Not theirs"}]}""".formatted(UUID.randomUUID()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.fields['routes[0].id']").exists());
        authed(put(path), admin, """
                {"stats":[{"key":"ROUTE_TIMES"}]}""")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.fields['stats[0].route_index']").exists());
        authed(put(path), admin, """
                {"stats":[{"key":"RATING","route_index":0}]}""")
                .andExpect(status().isBadRequest());
        authed(put(path), admin, """
                {"stats":[{"key":"RATING"},{"key":"RATING"},{"key":"RATING"},{"key":"RATING"},{"key":"RATING"},
                          {"key":"RATING"},{"key":"RATING"},{"key":"RATING"}]}""")
                .andExpect(status().isBadRequest());

        authed(put("/api/admin/guides/" + UUID.randomUUID() + "/record"), admin, "{}")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("GUIDE_NOT_FOUND"));
        authed(get(path), tokenWithRole(uniqueEmail(), "GUIDE"), null).andExpect(status().isForbidden());
    }

    private String trackName(UUID trackId) {
        return jdbc.queryForObject("SELECT name FROM tracks WHERE id = ?", String.class, trackId);
    }
}
