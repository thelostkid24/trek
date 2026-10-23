package com.sahyatri.guide;

import com.jayway.jsonpath.JsonPath;
import com.sahyatri.auth.AuthTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockMultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The guide's own profile, slugs and specialties (docs/TRD.md §7.21). */
class OwnGuideProfileTests extends AuthTestSupport {

    @Test
    void guideEditsTheirProfileAndThePublicPageShowsIt() throws Exception {
        String admin = adminToken();
        String email = uniqueEmail();
        String guide = tokenWithRole(email, "GUIDE");
        UUID guideId = userIdByEmail(email);
        UUID track = createTrack(admin, 2);
        UUID other = createTrack(admin, 2);
        UUID departure = createDraft(admin, track, guideId, today().plusDays(30), 199_900, 8);
        authed(post("/api/admin/departures/" + departure + "/publish"), admin, null).andExpect(status().isOk());

        String own = authed(get("/api/guide/profile"), guide, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.guide_id").value(guideId.toString()))
                .andExpect(jsonPath("$.slug", matchesPattern("^[a-z0-9]+(-[a-z0-9]+)*$")))
                .andExpect(jsonPath("$.routes[0].track_id").value(track.toString()))
                .andExpect(jsonPath("$.routes", hasSize(1)))
                .andReturn().getResponse().getContentAsString();
        List<String> options = JsonPath.read(own, "$.specialty_options[*].id");
        String slug = JsonPath.read(own, "$.slug");

        authed(put("/api/guide/profile"), guide, """
                {"full_name":" Shrey Rai ","home_city":"Sankri","bio":"Grew up in Osla.","quote":"Leads groups of six.",
                 "languages":"Hindi, Garhwali, English","leading_since":2015,
                 "specialty_ids":["%s","%s"],
                 "milestones":[{"year":2014,"approximate":true,"text":"Basic Mountaineering Course, NIM"},
                               {"year":2022,"month":11,"day":17,"text":"100th trek led"},{"text":"Undated"}],
                 "routes":[{"track_id":"%s","availability":"FROM_DATE","available_from":"%s",
                            "note":"Knows which campsites stay out of the wind."}]}"""
                .formatted(options.get(1), options.get(0), track, today().plusDays(30)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.full_name").value("Shrey Rai"))
                .andExpect(jsonPath("$.specialty_ids[0]").value(options.get(1)))
                .andExpect(jsonPath("$.milestones[1].text").value("100th trek led"))
                .andExpect(jsonPath("$.milestones[1].day").value(17))
                .andExpect(jsonPath("$.milestones[2].year").value(nullValue()))
                .andExpect(jsonPath("$.routes[0].id").isString())
                .andExpect(jsonPath("$.routes[0].track_id").value(track.toString()))
                .andExpect(jsonPath("$.routes[0].availability").value("FROM_DATE"));

        for (String key : List.of(slug, guideId.toString(), slug.toUpperCase())) {
            mockMvc.perform(get("/api/public/guides/" + key))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(guideId.toString()))
                    .andExpect(jsonPath("$.slug").value(slug))
                    .andExpect(jsonPath("$.full_name").value("Shrey Rai"))
                    .andExpect(jsonPath("$.home_city").value("Sankri"))
                    .andExpect(jsonPath("$.quote").value("Leads groups of six."))
                    .andExpect(jsonPath("$.years_leading").isNumber())
                    .andExpect(jsonPath("$.specialties", hasSize(2)))
                    .andExpect(jsonPath("$.milestones[0].year").value(2014))
                    .andExpect(jsonPath("$.milestones[0].approximate").value(true))
                    .andExpect(jsonPath("$.treks[0].note").value("Knows which campsites stay out of the wind."))
                    .andExpect(jsonPath("$.treks[0].times").value(nullValue()))
                    .andExpect(jsonPath("$.cover_url").value(nullValue()));
        }
        mockMvc.perform(get("/api/public/guides/nobody-" + UUID.randomUUID().toString().substring(0, 8)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("GUIDE_NOT_FOUND"));

        // A trek they don't lead, someone else's route id, a season without "any time", a duplicate specialty, a
        // future milestone, a day without a month, nine specialties.
        authed(put("/api/guide/profile"), guide, """
                {"full_name":"Shrey Rai","routes":[{"track_id":"%s","note":"Not mine."}]}""".formatted(other))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.fields['routes[0].track_id']").exists());
        authed(put("/api/guide/profile"), guide, """
                {"full_name":"Shrey Rai","routes":[{"id":"%s"}]}""".formatted(UUID.randomUUID()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.fields['routes[0].id']").exists());
        String routeId = JsonPath.read(authed(get("/api/guide/profile"), guide, null)
                .andReturn().getResponse().getContentAsString(), "$.routes[0].id");
        authed(put("/api/guide/profile"), guide, """
                {"full_name":"Shrey Rai","routes":[{"id":"%s","availability":"ASK","season_from_month":3,
                 "season_to_month":10}]}""".formatted(routeId))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.fields['routes[0].season_from_month']").exists());
        authed(put("/api/guide/profile"), guide, """
                {"full_name":"Shrey Rai","milestones":[{"year":2020,"day":3,"text":"No month"}]}""")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.fields['milestones[0].month']").exists());
        authed(put("/api/guide/profile"), guide, """
                {"full_name":"Shrey Rai","specialty_ids":["%s","%s"]}""".formatted(options.get(0), options.get(0)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.fields.specialty_ids").exists());
        authed(put("/api/guide/profile"), guide, """
                {"full_name":"Shrey Rai","milestones":[{"year":%d,"text":"Soon"}]}""".formatted(today().getYear() + 1))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.fields['milestones[0].year']").exists());
        authed(put("/api/guide/profile"), guide, """
                {"full_name":"","milestones":[{"year":2020,"text":""}]}""")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.fields.full_name").exists());

        // Null lists clear them; routes stay, since only admins remove routes.
        authed(put("/api/guide/profile"), guide, """
                {"full_name":"Shrey Rai"}""")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.specialty_ids", hasSize(0)))
                .andExpect(jsonPath("$.milestones", hasSize(0)))
                .andExpect(jsonPath("$.routes[0].note").value("Knows which campsites stay out of the wind."))
                .andExpect(jsonPath("$.bio").value(nullValue()));
    }

    @Test
    void coverPhotoIsCroppedToThreeByOne() throws Exception {
        String email = uniqueEmail();
        String guide = tokenWithRole(email, "GUIDE");
        UUID guideId = userIdByEmail(email);

        String body = authed(multipart(HttpMethod.PUT, "/api/guide/profile/cover")
                .file(new MockMultipartFile("file", "valley.png", "image/png", png(1200, 1200))), guide, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cover_url", startsWith("http")))
                .andReturn().getResponse().getContentAsString();
        String url = JsonPath.read(body, "$.cover_url");
        byte[] jpeg = mockMvc.perform(get(url.substring(url.indexOf("/api/"))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();
        BufferedImage stored = ImageIO.read(new ByteArrayInputStream(jpeg));
        assertThat(stored.getWidth()).isEqualTo(1800);
        assertThat(stored.getHeight()).isEqualTo(600);
        mockMvc.perform(get("/api/public/guides/" + guideId)).andExpect(jsonPath("$.cover_url").value(url));

        authed(delete("/api/guide/profile/cover"), guide, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cover_url").value(nullValue()));
    }

    @Test
    void onlyGuidesEditAGuideProfile() throws Exception {
        String trekker = emailTrekker(uniqueEmail());
        authed(get("/api/guide/profile"), trekker, null).andExpect(status().isForbidden());
        authed(put("/api/guide/profile"), trekker, """
                {"full_name":"Sneaky"}""").andExpect(status().isForbidden());
        mockMvc.perform(get("/api/guide/profile")).andExpect(status().isUnauthorized());
    }

    @Test
    void adminChangesTheSlugAndOldLinksKeepWorking() throws Exception {
        String admin = adminToken();
        String email = uniqueEmail();
        tokenWithRole(email, "GUIDE");
        UUID guideId = userIdByEmail(email);
        String first = "shrey-" + UUID.randomUUID().toString().substring(0, 8);
        String second = first + "-rai";

        authed(put("/api/admin/guides/" + guideId + "/slug"), admin, """
                {"slug":"%s"}""".formatted(first))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slug").value(first));
        authed(put("/api/admin/guides/" + guideId + "/slug"), admin, """
                {"slug":"%s"}""".formatted(second))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/public/guides/" + first))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slug").value(second));

        // Neither the current nor an old slug can go to another guide; bad shapes are rejected.
        String otherEmail = uniqueEmail();
        tokenWithRole(otherEmail, "GUIDE");
        UUID otherId = userIdByEmail(otherEmail);
        for (String taken : List.of(first, second)) {
            authed(put("/api/admin/guides/" + otherId + "/slug"), admin, """
                    {"slug":"%s"}""".formatted(taken))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("SLUG_TAKEN"));
        }
        authed(put("/api/admin/guides/" + otherId + "/slug"), admin, """
                {"slug":"Shrey Rai"}""")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.fields.slug").exists());

        // The guide can take back an old slug.
        authed(put("/api/admin/guides/" + guideId + "/slug"), admin, """
                {"slug":"%s"}""".formatted(first))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slug").value(first));
        mockMvc.perform(get("/api/public/guides/" + second)).andExpect(jsonPath("$.slug").value(first));
    }

    @Test
    void promotionGivesTheGuideASlug() throws Exception {
        String admin = adminToken();
        String email = uniqueEmail();
        emailTrekker(email);
        authed(post("/api/admin/guides"), admin, """
                {"email":"%s"}""".formatted(email)).andExpect(status().isCreated());
        String slug = jdbc.queryForObject("SELECT slug FROM guide_profiles WHERE user_id = ?", String.class,
                userIdByEmail(email));
        assertThat(slug).matches("^[a-z0-9]+(-[a-z0-9]+)*$");
    }

    @Test
    void adminKeepsTheSpecialtyList() throws Exception {
        String admin = adminToken();
        String name = "Night walks " + UUID.randomUUID().toString().substring(0, 6);
        String id = JsonPath.read(authed(post("/api/admin/guide-specialties"), admin, """
                {"name":"%s"}""".formatted(name))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(), "$.id");
        authed(post("/api/admin/guide-specialties"), admin, """
                {"name":"%s"}""".formatted(name.toUpperCase()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SPECIALTY_EXISTS"));
        authed(delete("/api/admin/guide-specialties/" + id), admin, null).andExpect(status().isNoContent());
        authed(delete("/api/admin/guide-specialties/" + id), admin, null)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SPECIALTY_NOT_FOUND"));
    }

    private static byte[] png(int width, int height) throws Exception {
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, "png", out);
        return out.toByteArray();
    }
}
