package com.sahyatri.snow;

import com.jayway.jsonpath.JsonPath;
import com.sahyatri.auth.AuthTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.UUID;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SnowReportTests extends AuthTestSupport {

    private static String report(String reportedOn, int tents) {
        return """
                {"reported_on":"%s","reported_from":" Sankri ","snowline_m":2900,"night_temp_c":-8,
                 "conditions":[{"label":"Juda ka Talab","value":"Frozen"},{"label":"Road, Purola to Sankri","value":"Open"}],
                 "crowd_place":"Juda ka Talab","crowd_tents":%d,"note":"Microspikes from day 3."}"""
                .formatted(reportedOn, tents);
    }

    @Test
    void reportsBuildAHistoryAndTheNewestShowsOnTheTrekPage() throws Exception {
        String admin = adminToken();
        UUID track = createTrack(admin, 2);
        String slug = jdbc.queryForObject("SELECT slug FROM tracks WHERE id = ?", String.class, track);
        String path = "/api/admin/tracks/" + track + "/snow-reports";

        authed(post(path), admin, report(today().minusDays(14).toString(), 40)).andExpect(status().isCreated());
        String body = authed(post(path), admin, report(today().toString(), 85))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.reported_from").value("Sankri"))
                .andExpect(jsonPath("$.conditions[0].value").value("Frozen"))
                .andExpect(jsonPath("$.photo_url").value(nullValue()))
                .andReturn().getResponse().getContentAsString();
        authed(post(path), admin, report(today().minusDays(7).toString(), 60)).andExpect(status().isCreated());

        authed(get(path), admin, null)
                .andExpect(jsonPath("$.items[*].crowd_tents", contains(85, 60, 40)));
        mockMvc.perform(get("/api/public/tracks/" + slug))
                .andExpect(jsonPath("$.snow_report.id").value((String) JsonPath.read(body, "$.id")))
                .andExpect(jsonPath("$.snow_report.snowline_m").value(2900))
                .andExpect(jsonPath("$.snow_report.night_temp_c").value(-8))
                .andExpect(jsonPath("$.crowd[*].tents", contains(40, 60, 85)))
                .andExpect(jsonPath("$.crowd[0].place").value("Juda ka Talab"));

        // One photo per report.
        String id = JsonPath.read(body, "$.id");
        String url = JsonPath.read(authed(multipart("/api/admin/snow-reports/" + id + "/photo")
                        .file(new MockMultipartFile("file", "p", "image/png", png())), admin, null)
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(), "$.photo_url");
        mockMvc.perform(get(url.replace("http://localhost:8081", ""))).andExpect(status().isOk());
        authed(multipart("/api/admin/snow-reports/" + id + "/photo")
                .file(new MockMultipartFile("file", "p", "image/png", png())), admin, null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REPORT_HAS_PHOTO"));
    }

    @Test
    void reportsAreChecked() throws Exception {
        String admin = adminToken();
        UUID track = createTrack(admin, 2);
        String path = "/api/admin/tracks/" + track + "/snow-reports";

        authed(post(path), admin, report(today().plusDays(1).toString(), 10))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.fields.reported_on").exists());
        authed(post(path), admin, """
                {"reported_on":"%s","reported_from":"Sankri","crowd_tents":12}""".formatted(today()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.fields.crowd_place").exists());
        // Only the date and place are required.
        authed(post(path), admin, """
                {"reported_on":"%s","reported_from":"Sankri"}""".formatted(today()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.conditions", hasSize(0)));
    }

    @Test
    void guidesReportOnlyOnTreksTheyLead() throws Exception {
        String admin = adminToken();
        UUID led = createTrack(admin, 2);
        UUID other = createTrack(admin, 2);
        String email = uniqueEmail();
        String guide = tokenWithRole(email, "GUIDE");
        UUID guideId = userIdByEmail(email);

        // A draft doesn't count; a published departure does.
        UUID draft = createDraft(admin, led, guideId, today().plusDays(30), 1_000_000, 10);
        authed(get("/api/guide/tracks"), guide, null).andExpect(jsonPath("$.items", hasSize(0)));
        authed(post("/api/guide/tracks/" + led + "/snow-reports"), guide, report(today().toString(), 5))
                .andExpect(status().isForbidden());
        authed(post("/api/admin/departures/" + draft + "/publish"), admin, null).andExpect(status().isOk());

        authed(get("/api/guide/tracks"), guide, null)
                .andExpect(jsonPath("$.items[*].id", contains(led.toString())));
        authed(post("/api/guide/tracks/" + led + "/snow-reports"), guide, report(today().toString(), 5))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.reported_by.id").value(guideId.toString()));
        authed(get("/api/guide/tracks/" + led + "/snow-reports"), guide, null)
                .andExpect(jsonPath("$.items", hasSize(1)));
        authed(post("/api/guide/tracks/" + other + "/snow-reports"), guide, report(today().toString(), 5))
                .andExpect(status().isForbidden());
        authed(post("/api/admin/tracks/" + led + "/snow-reports"), guide, report(today().toString(), 5))
                .andExpect(status().isForbidden());
    }

    private static byte[] png() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(40, 30, BufferedImage.TYPE_INT_RGB), "png", out);
        return out.toByteArray();
    }
}
