package com.sahyatri.guide;

import com.jayway.jsonpath.JsonPath;
import com.sahyatri.auth.AuthTestSupport;
import com.sahyatri.guide.service.GuideMediaCleanupJob;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.ResultActions;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Photos and videos on the guide profile (docs/TRD.md §7.21.4), against local storage. */
class GuideMediaTests extends AuthTestSupport {

    private static final byte[] VIDEO = "\0\0\0\u0018ftypmp42 pretend video bytes".getBytes();

    @Autowired
    private GuideMediaCleanupJob cleanup;

    @Test
    void photoIsReencodedServedAndListedPublicly() throws Exception {
        String email = uniqueEmail();
        String guide = tokenWithRole(email, "GUIDE");
        UUID guideId = userIdByEmail(email);

        String body = uploadPhoto(guide, png(3000, 1500), " Kedarkantha summit ")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.kind").value("PHOTO"))
                .andExpect(jsonPath("$.status").value("READY"))
                .andExpect(jsonPath("$.caption").value("Kedarkantha summit"))
                .andExpect(jsonPath("$.url", startsWith("http")))
                .andReturn().getResponse().getContentAsString();
        String url = JsonPath.read(body, "$.url");
        byte[] jpeg = mockMvc.perform(get(path(url)))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "image/jpeg"))
                .andReturn().getResponse().getContentAsByteArray();
        BufferedImage stored = ImageIO.read(new ByteArrayInputStream(jpeg));
        assertThat(stored.getWidth()).isEqualTo(2000);
        assertThat(stored.getHeight()).isEqualTo(1000);

        mockMvc.perform(get("/api/public/guides/" + guideId + "/media"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].url").value(url));

        uploadPhoto(guide, "not an image".getBytes(), null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_IMAGE"));
        uploadPhoto(guide, png(10, 10), "x".repeat(121))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.fields.caption").exists());
        mockMvc.perform(get("/api/public/guides/nobody-" + UUID.randomUUID().toString().substring(0, 8) + "/media"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("GUIDE_NOT_FOUND"));
    }

    @Test
    void videoGoesStraightToStorageThenPlaysWithRanges() throws Exception {
        String email = uniqueEmail();
        String guide = tokenWithRole(email, "GUIDE");
        UUID guideId = userIdByEmail(email);

        String started = startVideo(guide, "video/mp4", VIDEO.length, 42.3)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.headers['Content-Type']").value("video/mp4"))
                .andReturn().getResponse().getContentAsString();
        String id = JsonPath.read(started, "$.id");
        String uploadUrl = JsonPath.read(started, "$.upload_url");

        // Pending: not public, and confirming before the file arrives fails.
        mockMvc.perform(get("/api/public/guides/" + guideId + "/media")).andExpect(jsonPath("$.items", hasSize(0)));
        authed(get("/api/guide/media"), guide, null)
                .andExpect(jsonPath("$.items[0].status").value("PENDING"))
                .andExpect(jsonPath("$.items[0].duration_s").value(43));
        authed(post("/api/guide/media/videos/" + id + "/complete"), guide, null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("UPLOAD_MISSING"));

        // The upload link checks its signature, the type and the exact size.
        mockMvc.perform(put(URI.create(uploadUrl.replace("sig=", "sig=0"))).contentType("video/mp4").content(VIDEO))
                .andExpect(status().isForbidden());
        mockMvc.perform(put(URI.create(uploadUrl)).contentType("video/quicktime").content(VIDEO))
                .andExpect(status().isBadRequest());
        mockMvc.perform(put(URI.create(uploadUrl)).contentType("video/mp4").content(new byte[VIDEO.length + 1]))
                .andExpect(status().isBadRequest());
        mockMvc.perform(put(URI.create(uploadUrl)).contentType("video/mp4").content(VIDEO))
                .andExpect(status().isOk());

        authed(multipart(HttpMethod.PUT, "/api/guide/media/videos/" + id + "/poster")
                .file(new MockMultipartFile("file", "poster.jpg", "image/png", png(640, 360))), guide, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.poster_url", startsWith("http")));
        authed(post("/api/guide/media/videos/" + id + "/complete"), guide, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("READY"))
                .andExpect(jsonPath("$.size_bytes").value(VIDEO.length));

        String list = mockMvc.perform(get("/api/public/guides/" + guideId + "/media"))
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].kind").value("VIDEO"))
                .andReturn().getResponse().getContentAsString();
        String playUrl = JsonPath.read(list, "$.items[0].url");
        String posterUrl = JsonPath.read(list, "$.items[0].poster_url");
        mockMvc.perform(get(path(posterUrl))).andExpect(status().isOk());

        String location = mockMvc.perform(get(path(playUrl)))
                .andExpect(status().isFound())
                .andReturn().getResponse().getHeader(HttpHeaders.LOCATION);
        byte[] part = mockMvc.perform(get(URI.create(location)).header(HttpHeaders.RANGE, "bytes=4-7"))
                .andExpect(status().isPartialContent())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "video/mp4"))
                .andReturn().getResponse().getContentAsByteArray();
        assertThat(new String(part)).isEqualTo("ftyp");
        mockMvc.perform(get(URI.create(location.replace("sig=", "sig=0")))).andExpect(status().isForbidden());

        // Deleting removes the video and its poster.
        authed(delete("/api/guide/media/" + id), guide, null).andExpect(status().isNoContent());
        mockMvc.perform(get(path(playUrl))).andExpect(status().isNotFound());
        mockMvc.perform(get(path(posterUrl))).andExpect(status().isNotFound());
        mockMvc.perform(get(URI.create(location))).andExpect(status().isNotFound());
    }

    @Test
    void videoLimits() throws Exception {
        String guide = tokenWithRole(uniqueEmail(), "GUIDE");
        startVideo(guide, "video/webm", 1000, 10).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_VIDEO"));
        startVideo(guide, "video/mp4", 100L * 1024 * 1024 + 1, 10).andExpect(status().isContentTooLarge())
                .andExpect(jsonPath("$.code").value("FILE_TOO_LARGE"));
        startVideo(guide, "video/quicktime", 1000, 90.5).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.fields.duration_s").exists());
        startVideo(guide, "video/quicktime", 1000, 90).andExpect(status().isCreated());
    }

    @Test
    void atMostTwentyItemsReorderAndCaption() throws Exception {
        String guide = tokenWithRole(uniqueEmail(), "GUIDE");
        for (int i = 0; i < 19; i++) {
            uploadPhoto(guide, png(10, 10), "Photo " + i).andExpect(status().isCreated());
        }
        startVideo(guide, "video/mp4", 1000, 30).andExpect(status().isCreated());
        uploadPhoto(guide, png(10, 10), null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TOO_MANY_MEDIA"));
        startVideo(guide, "video/mp4", 1000, 30).andExpect(status().isConflict());

        String list = authed(get("/api/guide/media"), guide, null).andReturn().getResponse().getContentAsString();
        List<String> ids = JsonPath.read(list, "$.items[*].id");
        List<String> reversed = new ArrayList<>(ids);
        Collections.reverse(reversed);
        authed(put("/api/guide/media/order"), guide, json(reversed))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(ids.get(19)))
                .andExpect(jsonPath("$.items[0].position").value(0))
                .andExpect(jsonPath("$.items[19].id").value(ids.get(0)));
        authed(put("/api/guide/media/order"), guide, json(reversed.subList(1, 20)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.fields.ids").exists());
        List<String> repeated = new ArrayList<>(reversed.subList(1, 20));
        repeated.add(reversed.get(1));
        authed(put("/api/guide/media/order"), guide, json(repeated)).andExpect(status().isBadRequest());

        authed(put("/api/guide/media/" + ids.get(0)), guide, """
                {"caption":"  Dawn at Juda ka Talab "}""")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.caption").value("Dawn at Juda ka Talab"));
        authed(put("/api/guide/media/" + ids.get(0)), guide, """
                {"caption":" "}""")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.caption").value(nullValue()));

        // Deleting a photo removes its file and makes room again.
        String photoUrl = JsonPath.read(list, "$.items[0].url");
        authed(delete("/api/guide/media/" + ids.get(0)), guide, null).andExpect(status().isNoContent());
        mockMvc.perform(get(path(photoUrl))).andExpect(status().isNotFound());
        uploadPhoto(guide, png(10, 10), null).andExpect(status().isCreated());
    }

    @Test
    void guidesOnlyTouchTheirOwnMedia() throws Exception {
        String owner = tokenWithRole(uniqueEmail(), "GUIDE");
        String other = tokenWithRole(uniqueEmail(), "GUIDE");
        String id = JsonPath.read(uploadPhoto(owner, png(10, 10), null).andReturn().getResponse().getContentAsString(),
                "$.id");
        String video = JsonPath.read(startVideo(owner, "video/mp4", 1000, 5).andReturn().getResponse()
                .getContentAsString(), "$.id");

        authed(put("/api/guide/media/" + id), other, """
                {"caption":"Mine now"}""").andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("MEDIA_NOT_FOUND"));
        authed(delete("/api/guide/media/" + id), other, null).andExpect(status().isNotFound());
        authed(post("/api/guide/media/videos/" + video + "/complete"), other, null).andExpect(status().isNotFound());
        authed(get("/api/guide/media"), other, null).andExpect(jsonPath("$.items", hasSize(0)));
        authed(put("/api/guide/media/order"), other, json(List.of(id))).andExpect(status().isBadRequest());

        String trekker = emailTrekker(uniqueEmail());
        authed(get("/api/guide/media"), trekker, null).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/guide/media")).andExpect(status().isUnauthorized());
    }

    @Test
    void cleanupRemovesUnconfirmedVideosAfterADay() throws Exception {
        String guide = tokenWithRole(uniqueEmail(), "GUIDE");
        String started = startVideo(guide, "video/mp4", VIDEO.length, 12).andReturn().getResponse()
                .getContentAsString();
        String stale = JsonPath.read(started, "$.id");
        mockMvc.perform(put(URI.create(JsonPath.read(started, "$.upload_url"))).contentType("video/mp4").content(VIDEO))
                .andExpect(status().isOk());
        String fresh = JsonPath.read(startVideo(guide, "video/mp4", 1000, 12).andReturn().getResponse()
                .getContentAsString(), "$.id");
        jdbc.update("UPDATE guide_media SET created_at = now() - interval '25 hours' WHERE id = ?::uuid", stale);

        cleanup.run();

        assertThat(jdbc.queryForObject("SELECT count(*) FROM guide_media WHERE id = ?::uuid", Long.class, stale))
                .isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM guide_media WHERE id = ?::uuid", Long.class, fresh))
                .isOne();
        assertThat(UPLOAD_DIR.resolve("guide-videos/" + stale)).doesNotExist();
    }

    private ResultActions uploadPhoto(String token, byte[] bytes, String caption) throws Exception {
        var request = multipart("/api/guide/media/photos")
                .file(new MockMultipartFile("file", "photo.png", "image/png", bytes));
        if (caption != null) {
            request.param("caption", caption);
        }
        return authed(request, token, null);
    }

    private ResultActions startVideo(String token, String type, long size, double duration) throws Exception {
        return authed(post("/api/guide/media/videos"), token, """
                {"content_type":"%s","size_bytes":%d,"duration_s":%s}""".formatted(type, size, duration));
    }

    private static String json(List<String> ids) {
        return "{\"ids\":[" + String.join(",", ids.stream().map(i -> "\"" + i + "\"").toList()) + "]}";
    }

    /** MockMvc takes the path, not the absolute URL the API hands out. */
    private static String path(String url) {
        return url.substring(url.indexOf("/api/"));
    }

    private static byte[] png(int width, int height) throws Exception {
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, "png", out);
        return out.toByteArray();
    }
}
