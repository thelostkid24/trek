package com.sahyatri.guide;

import com.jayway.jsonpath.JsonPath;
import com.sahyatri.auth.AuthTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.ResultActions;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
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

/** Photos and YouTube videos on the guide profile (docs/TRD.md §7.21.4), against local storage. */
class GuideMediaTests extends AuthTestSupport {

    @Test
    void photoIsReencodedServedAndListedPublicly() throws Exception {
        String email = uniqueEmail();
        String guide = tokenWithRole(email, "GUIDE");
        UUID guideId = userIdByEmail(email);

        String body = uploadPhoto(guide, png(3000, 1500), " Kedarkantha summit ")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.kind").value("PHOTO"))
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
    void youTubeLinksAreAddedByVideoId() throws Exception {
        String email = uniqueEmail();
        String guide = tokenWithRole(email, "GUIDE");
        UUID guideId = userIdByEmail(email);

        addVideo(guide, "https://www.youtube.com/watch?v=dQw4w9WgXcQ&t=42s", " Meet Shrey, in his own words ")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.kind").value("VIDEO"))
                .andExpect(jsonPath("$.youtube_id").value("dQw4w9WgXcQ"))
                .andExpect(jsonPath("$.thumbnail_url").value("https://i.ytimg.com/vi/dQw4w9WgXcQ/hqdefault.jpg"))
                .andExpect(jsonPath("$.url").value(nullValue()))
                .andExpect(jsonPath("$.caption").value("Meet Shrey, in his own words"));
        for (String[] link : new String[][] {
                {"youtu.be/aaaaaaaaaa1?si=share", "aaaaaaaaaa1"},
                {"https://youtube.com/shorts/bbbbbbbbb-2", "bbbbbbbbb-2"},
                {"https://m.youtube.com/watch?feature=share&v=ccccccccc_3", "ccccccccc_3"},
                {"https://www.youtube-nocookie.com/embed/ddddddddd44", "ddddddddd44"},
                {"https://www.youtube.com/live/eeeeeeeee55", "eeeeeeeee55"}}) {
            addVideo(guide, link[0], null).andExpect(status().isCreated())
                    .andExpect(jsonPath("$.youtube_id").value(link[1]));
        }
        mockMvc.perform(get("/api/public/guides/" + guideId + "/media"))
                .andExpect(jsonPath("$.items", hasSize(6)))
                .andExpect(jsonPath("$.items[0].youtube_id").value("dQw4w9WgXcQ"));

        addVideo(guide, "https://youtu.be/dQw4w9WgXcQ", null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("VIDEO_ALREADY_ADDED"));
        for (String bad : List.of("https://vimeo.com/123456", "https://www.youtube.com/watch?v=short",
                "https://evil.example/watch?v=dQw4w9WgXcQ", "https://www.youtube.com/@channel", "not a link",
                "javascript:alert(1)")) {
            addVideo(guide, bad, null)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("UNSUPPORTED_VIDEO_LINK"));
        }
        addVideo(guide, "", null).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.fields.url").exists());
    }

    @Test
    void atMostTwentyItemsReorderAndCaption() throws Exception {
        String guide = tokenWithRole(uniqueEmail(), "GUIDE");
        for (int i = 0; i < 19; i++) {
            uploadPhoto(guide, png(10, 10), "Photo " + i).andExpect(status().isCreated());
        }
        addVideo(guide, "https://youtu.be/ffffffffff6", null).andExpect(status().isCreated());
        uploadPhoto(guide, png(10, 10), null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TOO_MANY_MEDIA"));
        addVideo(guide, "https://youtu.be/gggggggggg7", null).andExpect(status().isConflict());

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
        String video = JsonPath.read(addVideo(owner, "https://youtu.be/hhhhhhhhhh8", null).andReturn().getResponse()
                .getContentAsString(), "$.id");

        authed(put("/api/guide/media/" + id), other, """
                {"caption":"Mine now"}""").andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("MEDIA_NOT_FOUND"));
        authed(delete("/api/guide/media/" + id), other, null).andExpect(status().isNotFound());
        authed(delete("/api/guide/media/" + video), other, null).andExpect(status().isNotFound());
        authed(get("/api/guide/media"), other, null).andExpect(jsonPath("$.items", hasSize(0)));
        authed(put("/api/guide/media/order"), other, json(List.of(id))).andExpect(status().isBadRequest());

        String trekker = emailTrekker(uniqueEmail());
        authed(get("/api/guide/media"), trekker, null).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/guide/media")).andExpect(status().isUnauthorized());
    }

    @Test
    void anAdminManagesAGuidesGallery() throws Exception {
        String admin = adminToken();
        String email = uniqueEmail();
        String guide = tokenWithRole(email, "GUIDE");
        UUID guideId = userIdByEmail(email);
        String base = "/api/admin/guides/" + guideId + "/media";

        String id = JsonPath.read(authed(multipart(base + "/photos")
                .file(new MockMultipartFile("file", "photo.png", "image/png", png(40, 30))), admin, null)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.kind").value("PHOTO"))
                .andReturn().getResponse().getContentAsString(), "$.id");
        authed(get(base), admin, null).andExpect(jsonPath("$.items[*].id", contains(id)));
        authed(get("/api/guide/media"), guide, null).andExpect(jsonPath("$.items[*].id", contains(id)));
        mockMvc.perform(get("/api/public/guides/" + guideId + "/media")).andExpect(jsonPath("$.items", hasSize(1)));

        authed(delete(base + "/" + id), admin, null).andExpect(status().isNoContent());
        authed(get(base), admin, null).andExpect(jsonPath("$.items", hasSize(0)));

        String trekkerEmail = uniqueEmail();
        emailTrekker(trekkerEmail);
        authed(get("/api/admin/guides/" + userIdByEmail(trekkerEmail) + "/media"), admin, null)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("GUIDE_NOT_FOUND"));
        authed(get(base), guide, null).andExpect(status().isForbidden());
    }

    private ResultActions uploadPhoto(String token, byte[] bytes, String caption) throws Exception {
        var request = multipart("/api/guide/media/photos")
                .file(new MockMultipartFile("file", "photo.png", "image/png", bytes));
        if (caption != null) {
            request.param("caption", caption);
        }
        return authed(request, token, null);
    }

    private ResultActions addVideo(String token, String url, String caption) throws Exception {
        return authed(post("/api/guide/media/videos"), token, caption == null
                ? "{\"url\":\"%s\"}".formatted(url)
                : "{\"url\":\"%s\",\"caption\":\"%s\"}".formatted(url, caption));
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
