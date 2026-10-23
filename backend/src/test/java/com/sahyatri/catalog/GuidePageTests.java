package com.sahyatri.catalog;

import com.sahyatri.auth.AuthTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockMultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The guide page's read-time stats and its link-preview image (docs/TRD.md §7.21.2). */
class GuidePageTests extends AuthTestSupport {

    @Test
    void highestPassAndReturnRateAreWorkedOutAtReadTime() throws Exception {
        String admin = adminToken();
        UUID guide = guideUser();
        UUID kedarkantha = createTrack(admin, 2);
        UUID rajmachi = createTrack(admin, 2);
        UUID rupin = createTrack(admin, 2);
        jdbc.update("UPDATE tracks SET max_altitude_m = 3810, season_label = 'Snow trek · Dec–Apr' WHERE id = ?", kedarkantha);
        jdbc.update("UPDATE tracks SET max_altitude_m = 4650 WHERE id = ?", rupin);
        // Rupin was led before Sahyātri; it still counts towards the highest pass.
        authed(put("/api/admin/guides/" + guide + "/record"), admin, """
                {"routes":[{"track_id":"%s","times_led":2}]}""".formatted(rupin)).andExpect(status().isOk());

        UUID first = publish(admin, kedarkantha, guide, 30);
        UUID second = publish(admin, rajmachi, guide, 40);
        List<String> trekkers = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            String t = bookingTrekker();
            trekkers.add(t);
            confirmedBooking(t, first, 1);
        }
        for (String t : trekkers.subList(0, 3)) {
            confirmedBooking(t, second, 1);
        }

        // Nobody has walked with them yet: no return rate.
        mockMvc.perform(get("/api/public/guides/" + guide))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.highest_altitude_m").value(4650))
                .andExpect(jsonPath("$.return_rate_pct").value(nullValue()));

        jdbc.update("UPDATE departures SET status = 'COMPLETED' WHERE id = ?", first);
        mockMvc.perform(get("/api/public/guides/" + guide))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.highest_altitude_m").value(4650))
                .andExpect(jsonPath("$.return_rate_pct").value(30))
                .andExpect(jsonPath("$.treks[1].max_altitude_m").value(3810))
                .andExpect(jsonPath("$.treks[1].season_label").value("Snow trek · Dec–Apr"))
                .andExpect(jsonPath("$.treks[1].times").value(1));

        // Under ten trekkers the rate stays hidden: one booking cancelled leaves nine.
        jdbc.update("""
                UPDATE bookings SET status = 'CANCELLED_BY_TREKKER', cancelled_at = now()
                WHERE departure_id = ? AND user_id = (SELECT user_id FROM bookings WHERE departure_id = ? LIMIT 1)""",
                first, first);
        mockMvc.perform(get("/api/public/guides/" + guide))
                .andExpect(jsonPath("$.return_rate_pct").value(nullValue()));
    }

    @Test
    void linkPreviewIsA1200By630Png() throws Exception {
        String admin = adminToken();
        String email = uniqueEmail();
        String token = tokenWithRole(email, "GUIDE");
        UUID guide = userIdByEmail(email);
        String slug = com.jayway.jsonpath.JsonPath.read(authed(get("/api/guide/profile"), token, null)
                .andReturn().getResponse().getContentAsString(), "$.slug");

        // Initials, no numbers yet.
        assertCard("/api/public/guides/" + slug + "/card.png");

        authed(put("/api/admin/guides/" + guide + "/details"), admin, """
                {"bmc_institute":"NIM, Uttarkashi","bmc_certificate_number":"BMC-0412"}""").andExpect(status().isOk());
        authed(multipart(HttpMethod.PUT, "/api/guide/profile/avatar")
                .file(new MockMultipartFile("file", "me.png", "image/png", png(400, 300))), token, null)
                .andExpect(status().isOk());
        assertCard("/api/public/guides/" + guide + "/card.png");
        assertCard("/api/public/guides/" + slug.toUpperCase() + "/card.png");

        mockMvc.perform(get("/api/public/guides/nobody-" + UUID.randomUUID().toString().substring(0, 8) + "/card.png"))
                .andExpect(status().isNotFound());
    }

    private void assertCard(String path) throws Exception {
        byte[] png = mockMvc.perform(get(path))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "image/png"))
                .andExpect(header().string("Cache-Control", containsString("max-age=3600")))
                .andExpect(header().string("Cache-Control", containsString("public")))
                .andReturn().getResponse().getContentAsByteArray();
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(png));
        assertThat(image.getWidth()).isEqualTo(1200);
        assertThat(image.getHeight()).isEqualTo(630);
    }

    private UUID publish(String admin, UUID track, UUID guide, int daysOut) throws Exception {
        UUID id = createDraft(admin, track, guide, today().plusDays(daysOut), 199_900, 10);
        authed(post("/api/admin/departures/" + id + "/publish"), admin, null).andExpect(status().isOk());
        return id;
    }

    private static byte[] png(int width, int height) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB), "png", out);
        return out.toByteArray();
    }
}
