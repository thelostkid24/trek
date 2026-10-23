package com.sahyatri.guide;

import com.jayway.jsonpath.JsonPath;
import com.sahyatri.auth.AuthTestSupport;
import com.sahyatri.common.config.GuideTierProperties;
import com.sahyatri.guide.dto.GuideTier;
import com.sahyatri.guide.service.GuideTierService;
import com.sahyatri.review.dto.RatingSummary;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockMultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Guide certifications, their private documents, admin verification and tiers (docs/TRD.md §7.21.3). */
class GuideCertificationTests extends AuthTestSupport {

    private static final byte[] PDF = "%PDF-1.4\n1 0 obj <<>> endobj\ntrailer <<>>\n%%EOF\n"
            .getBytes(StandardCharsets.US_ASCII);

    @Autowired
    GuideTierProperties tierProps;

    @Test
    void guideAddsEditsAndRemovesTheirCertificates() throws Exception {
        String email = uniqueEmail();
        String guide = tokenWithRole(email, "GUIDE");

        String id = create(guide, """
                {"type":"BMC","issuer":" Nehru Institute of Mountaineering, Uttarkashi ","year":2014,"number":"BMC/1234"}""");
        create(guide, """
                {"type":"WILDERNESS_FIRST_RESPONDER","issuer":"NOLS","valid_to":"%s"}""".formatted(today().plusYears(1)));

        authed(get("/api/guide/certifications"), guide, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(2)))
                .andExpect(jsonPath("$.items[0].type").value("BMC"))
                .andExpect(jsonPath("$.items[0].issuer").value("Nehru Institute of Mountaineering, Uttarkashi"))
                .andExpect(jsonPath("$.items[0].status").value("PENDING"))
                .andExpect(jsonPath("$.items[0].document_url").value(nullValue()))
                .andExpect(jsonPath("$.items[1].valid_to").value(today().plusYears(1).toString()));

        authed(put("/api/guide/certifications/" + id), guide, """
                {"type":"BMC","issuer":"ABVIMAS, Manali","year":2015,"number":""}""")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.issuer").value("ABVIMAS, Manali"))
                .andExpect(jsonPath("$.number").value(nullValue()));

        // OTHER needs a title; years can't be in the future; the type must be known.
        authed(post("/api/guide/certifications"), guide, """
                {"type":"OTHER","issuer":"Red Cross"}""")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.fields.title").exists());
        authed(post("/api/guide/certifications"), guide, """
                {"type":"FIRST_AID","year":%d}""".formatted(today().getYear() + 1))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.fields.year").exists());
        authed(post("/api/guide/certifications"), guide, """
                {"type":"PILOT"}""")
                .andExpect(status().isBadRequest());
        authed(post("/api/guide/certifications"), guide, """
                {"type":"OTHER","title":"Avalanche awareness","issuer":"HMI Darjeeling"}""")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("Avalanche awareness"));

        authed(delete("/api/guide/certifications/" + id), guide, null).andExpect(status().isNoContent());
        authed(get("/api/guide/certifications"), guide, null).andExpect(jsonPath("$.items", hasSize(2)));
    }

    @Test
    void guidesTouchOnlyTheirOwnCertificates() throws Exception {
        String owner = tokenWithRole(uniqueEmail(), "GUIDE");
        String other = tokenWithRole(uniqueEmail(), "GUIDE");
        String id = create(owner, """
                {"type":"FIRST_AID","issuer":"St John Ambulance"}""");

        authed(put("/api/guide/certifications/" + id), other, """
                {"type":"FIRST_AID","issuer":"Mine now"}""")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CERTIFICATION_NOT_FOUND"));
        authed(delete("/api/guide/certifications/" + id), other, null).andExpect(status().isNotFound());
        authed(multipart(HttpMethod.PUT, "/api/guide/certifications/" + id + "/document")
                .file(pdf()), other, null).andExpect(status().isNotFound());
        authed(get("/api/guide/certifications"), other, null).andExpect(jsonPath("$.items", hasSize(0)));

        String trekker = emailTrekker(uniqueEmail());
        authed(get("/api/guide/certifications"), trekker, null).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/guide/certifications")).andExpect(status().isUnauthorized());
    }

    @Test
    void documentsArePrivate() throws Exception {
        String admin = adminToken();
        String email = uniqueEmail();
        String guide = tokenWithRole(email, "GUIDE");
        UUID guideId = userIdByEmail(email);
        String id = create(guide, """
                {"type":"INSURANCE","issuer":"Tata AIG","number":"POL-998877","valid_to":"%s"}"""
                .formatted(today().plusMonths(6)));

        String body = authed(multipart(HttpMethod.PUT, "/api/guide/certifications/" + id + "/document")
                .file(pdf()), guide, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.document_url").value("/api/guide/certifications/" + id + "/document"))
                .andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain("guide-certificates/");
        String key = jdbc.queryForObject("SELECT document_key FROM guide_certifications WHERE id = ?::uuid",
                String.class, id);
        assertThat(key).startsWith("guide-certificates/").endsWith(".pdf");

        // The guide and admins download it; nobody else does.
        byte[] own = authed(get("/api/guide/certifications/" + id + "/document"), guide, null)
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/pdf"))
                .andExpect(header().string("Content-Disposition", containsString("attachment")))
                .andExpect(header().string("Cache-Control", containsString("no-store")))
                .andReturn().getResponse().getContentAsByteArray();
        assertThat(own).isEqualTo(PDF);
        String adminPath = "/api/admin/guides/" + guideId + "/certifications/" + id + "/document";
        authed(get("/api/admin/guides/" + guideId + "/certifications"), admin, null)
                .andExpect(jsonPath("$.items[0].document_url").value(adminPath));
        byte[] asAdmin = authed(get(adminPath), admin, null).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();
        assertThat(asAdmin).isEqualTo(PDF);

        String other = tokenWithRole(uniqueEmail(), "GUIDE");
        authed(get("/api/guide/certifications/" + id + "/document"), other, null).andExpect(status().isNotFound());
        authed(get(adminPath), guide, null).andExpect(status().isForbidden());
        mockMvc.perform(get(adminPath)).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/guide/certifications/" + id + "/document")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/public/files/" + key)).andExpect(status().isNotFound());

        // The public page never names the document, nor the number of anything but BMC/AMC.
        authed(post("/api/admin/guides/" + guideId + "/certifications/" + id + "/verify"), admin, null)
                .andExpect(status().isOk());
        String publicBody = mockMvc.perform(get("/api/public/guides/" + guideId + "/credentials"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].type").value("INSURANCE"))
                .andExpect(jsonPath("$.items[0].number").value(nullValue()))
                .andReturn().getResponse().getContentAsString();
        assertThat(publicBody).doesNotContain("document").doesNotContain("POL-998877");

        // Photos are re-encoded to JPEG; anything else is refused.
        authed(multipart(HttpMethod.PUT, "/api/guide/certifications/" + id + "/document")
                .file(new MockMultipartFile("file", "card.png", "image/png", png())), guide, null)
                .andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT document_key FROM guide_certifications WHERE id = ?::uuid",
                String.class, id)).endsWith(".jpg");
        authed(get("/api/guide/certifications/" + id + "/document"), guide, null)
                .andExpect(header().string("Content-Type", "image/jpeg"));
        authed(multipart(HttpMethod.PUT, "/api/guide/certifications/" + id + "/document")
                .file(new MockMultipartFile("file", "x.html", "text/html", "<html></html>".getBytes())), guide, null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_DOCUMENT"));
    }

    @Test
    void adminVerifiesAndEditsResetIt() throws Exception {
        String adminEmail = uniqueEmail();
        String admin = tokenWithRole(adminEmail, "ADMIN");
        UUID adminId = userIdByEmail(adminEmail);
        String email = uniqueEmail();
        String guide = tokenWithRole(email, "GUIDE");
        UUID guideId = userIdByEmail(email);
        String bmc = """
                {"type":"BMC","issuer":"NIM, Uttarkashi","year":2014,"number":"BMC/1234"}""";
        String id = create(guide, bmc);
        String verifyPath = "/api/admin/guides/" + guideId + "/certifications/" + id + "/verify";

        // Pending rows show publicly as pending, but never count as verified.
        mockMvc.perform(get("/api/public/guides/" + guideId + "/credentials"))
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].status").value("PENDING"))
                .andExpect(jsonPath("$.verified").value(false))
                .andExpect(jsonPath("$.tier").value(nullValue()));

        authed(post(verifyPath), admin, """
                {"note":"Checked the original at the Sankri office"}""")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("VERIFIED"))
                .andExpect(jsonPath("$.verified_at").exists());
        Map<String, Object> event = jdbc.queryForMap("""
                SELECT actor_id, entity_type, data::text AS data FROM audit_events
                WHERE action = 'CERTIFICATION_VERIFIED' AND entity_id = ?::uuid""", id);
        assertThat(event.get("actor_id")).isEqualTo(adminId);
        assertThat(event.get("entity_type")).isEqualTo("GUIDE_CERTIFICATION");
        assertThat((String) event.get("data")).contains("Sankri office").contains(guideId.toString());

        mockMvc.perform(get("/api/public/guides/" + guideId + "/credentials"))
                .andExpect(jsonPath("$.verified").value(true))
                .andExpect(jsonPath("$.items[0].status").value("VERIFIED"))
                .andExpect(jsonPath("$.items[0].number").value("BMC/1234"));

        // Saving the same details keeps it; a real change drops it.
        authed(put("/api/guide/certifications/" + id), guide, bmc)
                .andExpect(jsonPath("$.status").value("VERIFIED"));
        authed(put("/api/guide/certifications/" + id), guide, """
                {"type":"BMC","issuer":"NIM, Uttarkashi","year":2014,"number":"BMC/9999"}""")
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.verified_at").value(nullValue()));
        mockMvc.perform(get("/api/public/guides/" + guideId + "/credentials"))
                .andExpect(jsonPath("$.items[0].status").value("PENDING"))
                .andExpect(jsonPath("$.verified").value(false));

        // A new document drops it too.
        authed(post(verifyPath), admin, null).andExpect(status().isOk());
        authed(multipart(HttpMethod.PUT, "/api/guide/certifications/" + id + "/document").file(pdf()), guide, null)
                .andExpect(jsonPath("$.status").value("PENDING"));

        authed(post(verifyPath), admin, null).andExpect(status().isOk());
        authed(post("/api/admin/guides/" + guideId + "/certifications/" + id + "/unverify"), admin, """
                {"note":"Number doesn't match NIM's register"}""")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"));
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM audit_events WHERE action = 'CERTIFICATION_UNVERIFIED' AND entity_id = ?::uuid""",
                Integer.class, id)).isEqualTo(1);

        // Another guide's certificate isn't reachable under this guide; non-guides are not found.
        String otherEmail = uniqueEmail();
        tokenWithRole(otherEmail, "GUIDE");
        authed(post("/api/admin/guides/" + userIdByEmail(otherEmail) + "/certifications/" + id + "/verify"), admin, null)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CERTIFICATION_NOT_FOUND"));
        authed(get("/api/admin/guides/" + UUID.randomUUID() + "/certifications"), admin, null)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("GUIDE_NOT_FOUND"));
        authed(post(verifyPath), guide, null).andExpect(status().isForbidden());
    }

    @Test
    void pastValidToShowsExpiredNotVerified() throws Exception {
        String admin = adminToken();
        String email = uniqueEmail();
        String guide = tokenWithRole(email, "GUIDE");
        UUID guideId = userIdByEmail(email);
        String lapsed = create(guide, """
                {"type":"WILDERNESS_FIRST_RESPONDER","issuer":"NOLS","valid_to":"%s"}""".formatted(today().minusDays(1)));
        String lastDay = create(guide, """
                {"type":"INSURANCE","issuer":"Tata AIG","valid_to":"%s"}""".formatted(today()));
        for (String id : List.of(lapsed, lastDay)) {
            authed(post("/api/admin/guides/" + guideId + "/certifications/" + id + "/verify"), admin, null)
                    .andExpect(status().isOk());
        }

        authed(get("/api/guide/certifications"), guide, null)
                .andExpect(jsonPath("$.items[0].status").value("EXPIRED"))
                .andExpect(jsonPath("$.items[1].status").value("VERIFIED"));
        mockMvc.perform(get("/api/public/guides/" + guideId + "/credentials"))
                .andExpect(jsonPath("$.items", hasSize(2)))
                .andExpect(jsonPath("$.items[0].type").value("WILDERNESS_FIRST_RESPONDER"))
                .andExpect(jsonPath("$.items[0].status").value("EXPIRED"))
                .andExpect(jsonPath("$.items[1].status").value("VERIFIED"))
                .andExpect(jsonPath("$.verified").value(true));

        // With only an expired certificate, the page doesn't claim "verified".
        authed(delete("/api/guide/certifications/" + lastDay), guide, null).andExpect(status().isNoContent());
        mockMvc.perform(get("/api/public/guides/" + guideId + "/credentials"))
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.verified").value(false));
    }

    @Test
    void trailGuideNeedsAVerifiedBmcAndFiveCompletedTreks() throws Exception {
        String admin = adminToken();
        String email = uniqueEmail();
        String guide = tokenWithRole(email, "GUIDE");
        UUID guideId = userIdByEmail(email);
        UUID track = createTrack(admin, 2);
        String slug = JsonPath.read(authed(get("/api/guide/profile"), guide, null)
                .andReturn().getResponse().getContentAsString(), "$.slug");
        String bmc = create(guide, """
                {"type":"BMC","issuer":"NIM, Uttarkashi"}""");
        authed(post("/api/admin/guides/" + guideId + "/certifications/" + bmc + "/verify"), admin, null);
        completedDepartures(track, guideId, 4);
        // Treks led before Sahyātri don't count.
        jdbc.update("""
                INSERT INTO guide_routes (id, guide_id, track_id, times_led, position, created_at, updated_at)
                VALUES (gen_random_uuid(), ?, ?, 40, 0, now(), now())""", guideId, track);

        mockMvc.perform(get("/api/public/guides/" + slug + "/credentials"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tier").value(nullValue()));
        completedDepartures(track, guideId, 1);
        mockMvc.perform(get("/api/public/guides/" + slug + "/credentials"))
                .andExpect(jsonPath("$.tier").value("TRAIL_GUIDE"));

        // The tier follows the certificate: unverified, it's gone.
        authed(post("/api/admin/guides/" + guideId + "/certifications/" + bmc + "/unverify"), admin, null);
        mockMvc.perform(get("/api/public/guides/" + slug + "/credentials"))
                .andExpect(jsonPath("$.tier").value(nullValue()));

        mockMvc.perform(get("/api/public/guides/nobody-here/credentials"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("GUIDE_NOT_FOUND"));
    }

    @Test
    void tierThresholdsIncludingBoundaries() {
        // Trail: verified BMC + 5 treks.
        assertThat(tier(true, false, 5, null, 0)).isEqualTo(GuideTier.TRAIL_GUIDE);
        assertThat(tier(true, false, 4, null, 0)).isNull();
        assertThat(tier(false, false, 24, 5.0, 100)).isNull();

        // Senior: 25 treks and ≥ 4.5 from ≥ 10 reviews, no certificate needed.
        assertThat(tier(false, false, 25, 4.5, 10)).isEqualTo(GuideTier.SENIOR_GUIDE);
        assertThat(tier(false, false, 24, 4.9, 50)).isNull();
        assertThat(tier(false, false, 25, 4.4, 50)).isNull();
        assertThat(tier(false, false, 25, 4.9, 9)).isNull();
        assertThat(tier(true, false, 25, 4.4, 50)).isEqualTo(GuideTier.TRAIL_GUIDE);

        // Summit: verified AMC + 75 treks + ≥ 4.7 from ≥ 25 reviews.
        assertThat(tier(true, true, 75, 4.7, 25)).isEqualTo(GuideTier.SUMMIT_GUIDE);
        assertThat(tier(false, true, 75, 4.7, 25)).isEqualTo(GuideTier.SUMMIT_GUIDE);
        assertThat(tier(true, true, 74, 4.9, 100)).isEqualTo(GuideTier.SENIOR_GUIDE);
        assertThat(tier(true, true, 75, 4.6, 100)).isEqualTo(GuideTier.SENIOR_GUIDE);
        assertThat(tier(true, true, 75, 4.9, 24)).isEqualTo(GuideTier.SENIOR_GUIDE);
        assertThat(tier(true, false, 200, 5.0, 200)).isEqualTo(GuideTier.SENIOR_GUIDE);
        assertThat(tier(false, false, 0, null, 0)).isNull();
    }

    @Test
    void v26CopiesTheOldCourseColumnsAsVerifiedRows() throws Exception {
        String email = uniqueEmail();
        tokenWithRole(email, "GUIDE");
        UUID guideId = userIdByEmail(email);
        jdbc.update("""
                INSERT INTO guide_profiles (user_id, bmc_institute, bmc_certificate_number, amc_institute,
                                            certification, certification_number, created_at, updated_at)
                VALUES (?, 'NIM, Uttarkashi', 'BMC/42', '  ', 'Wilderness First Aid', 'WFA-7', now(), now())
                ON CONFLICT (user_id) DO UPDATE SET bmc_institute = EXCLUDED.bmc_institute,
                    bmc_certificate_number = EXCLUDED.bmc_certificate_number, amc_institute = EXCLUDED.amc_institute,
                    certification = EXCLUDED.certification, certification_number = EXCLUDED.certification_number""",
                guideId);

        runV26Copy();
        runV26Copy(); // safe to run twice

        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT type, title, issuer, number, verified_at, verified_by FROM guide_certifications
                WHERE guide_id = ? ORDER BY type""", guideId);
        assertThat(rows).hasSize(2);
        assertThat(rows.get(0)).containsEntry("type", "BMC").containsEntry("issuer", "NIM, Uttarkashi")
                .containsEntry("number", "BMC/42").containsEntry("verified_by", null);
        assertThat(rows.get(0).get("verified_at")).isNotNull();
        assertThat(rows.get(1)).containsEntry("type", "OTHER").containsEntry("title", "Wilderness First Aid")
                .containsEntry("number", "WFA-7");
        mockMvc.perform(get("/api/public/guides/" + guideId + "/credentials"))
                .andExpect(jsonPath("$.items", hasSize(2)))
                .andExpect(jsonPath("$.items[0].type").value("BMC"))
                .andExpect(jsonPath("$.items[1].number").value(nullValue()))
                .andExpect(jsonPath("$.verified").value(true));
    }

    private GuideTier tier(boolean bmc, boolean amc, long treks, Double average, long reviews) {
        return GuideTierService.tierFor(tierProps, bmc, amc, treks, new RatingSummary(average, reviews));
    }

    /** Re-runs the data-copy half of V26 (everything after its "-- Copy:" comment). */
    private void runV26Copy() throws Exception {
        String sql = new ClassPathResource("db/migration/V26__guide_certifications.sql")
                .getContentAsString(StandardCharsets.UTF_8);
        for (String statement : sql.substring(sql.indexOf("-- Copy:")).split(";")) {
            if (statement.contains("INSERT")) {
                jdbc.execute(statement);
            }
        }
    }

    private void completedDepartures(UUID track, UUID guideId, int count) {
        for (int i = 0; i < count; i++) {
            jdbc.update("""
                    INSERT INTO departures (id, track_id, guide_id, start_date, end_date, price_paise, max_group_size,
                                            status, guide_share_bps, published_at, created_at, updated_at)
                    VALUES (gen_random_uuid(), ?, ?, ?, ?, 199900, 6, 'COMPLETED', 7000, now(), now(), now())""",
                    track, guideId, today().minusDays(30 + i * 7L), today().minusDays(29 + i * 7L));
        }
    }

    private String create(String token, String json) throws Exception {
        String body = authed(post("/api/guide/certifications"), token, json)
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    private static MockMultipartFile pdf() {
        return new MockMultipartFile("file", "bmc.pdf", "application/pdf", PDF);
    }

    private static byte[] png() throws Exception {
        BufferedImage image = new BufferedImage(40, 30, BufferedImage.TYPE_INT_ARGB);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }
}
