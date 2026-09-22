package com.sahyatri.catalog;

import com.jayway.jsonpath.JsonPath;
import com.sahyatri.auth.AuthTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TrackPhotoTests extends AuthTestSupport {

    @Test
    void uploadedPhotosShowOnTheTrekPageInOrder() throws Exception {
        String admin = adminToken();
        UUID track = createTrack(admin, 2);
        String slug = jdbc.queryForObject("SELECT slug FROM tracks WHERE id = ?", String.class, track);

        String first = photoId(upload(admin, track, image("jpg", 4000, 3000), "  Summit at first light  "));
        String second = photoId(upload(admin, track, image("png", 600, 900), ""));

        mockMvc.perform(get("/api/public/tracks/" + slug))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.track.photos[*].id", contains(first, second)))
                .andExpect(jsonPath("$.track.photos[0].caption").value("Summit at first light"))
                .andExpect(jsonPath("$.track.photos[1].caption").value(nullValue()));
        authed(get("/api/admin/tracks"), admin, null)
                .andExpect(jsonPath("$.items[?(@.id == '" + track + "')].photos[*].id", contains(first, second)));

        // Long edge scaled down to 2000px, aspect kept; small photos aren't enlarged.
        assertSize(first, 2000, 1500);
        assertSize(second, 600, 900);
    }

    @Test
    void deleteRemovesThePhotoAndItsFile() throws Exception {
        String admin = adminToken();
        UUID track = createTrack(admin, 1);
        String id = photoId(upload(admin, track, image("png", 100, 100), null));

        authed(delete("/api/admin/tracks/" + UUID.randomUUID() + "/photos/" + id), admin, null)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PHOTO_NOT_FOUND"));
        authed(delete("/api/admin/tracks/" + track + "/photos/" + id), admin, null).andExpect(status().isNoContent());

        mockMvc.perform(get(filePath(id))).andExpect(status().isNotFound());
        assertThat(UPLOAD_DIR.resolve("track-photos").resolve(id + ".jpg")).doesNotExist();
        authed(delete("/api/admin/tracks/" + track + "/photos/" + id), admin, null).andExpect(status().isNotFound());
    }

    @Test
    void rejectsBadUploads() throws Exception {
        String admin = adminToken();
        UUID track = createTrack(admin, 1);

        upload(admin, track, "not an image".getBytes(), null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_IMAGE"));
        upload(admin, track, image("png", 10, 10), "x".repeat(201))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.fields.caption").exists());
        upload(admin, UUID.randomUUID(), image("png", 10, 10), null)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TRACK_NOT_FOUND"));

        jdbc.update("""
                INSERT INTO track_photos (id, track_id, created_at)
                SELECT gen_random_uuid(), ?, now() FROM generate_series(1, 30)""", track);
        upload(admin, track, image("png", 10, 10), null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TOO_MANY_PHOTOS"));
    }

    @Test
    void onlyAdminsUpload() throws Exception {
        UUID track = createTrack(adminToken(), 1);
        upload(emailTrekker(uniqueEmail()), track, image("png", 10, 10), null).andExpect(status().isForbidden());
        mockMvc.perform(photoRequest(track, image("png", 10, 10), null)).andExpect(status().isUnauthorized());
    }

    private ResultActions upload(String token, UUID track, byte[] bytes, String caption) throws Exception {
        return authed(photoRequest(track, bytes, caption), token, null);
    }

    private static MockMultipartHttpServletRequestBuilder photoRequest(UUID track, byte[] bytes, String caption) {
        MockMultipartHttpServletRequestBuilder request = multipart("/api/admin/tracks/" + track + "/photos")
                .file(new MockMultipartFile("file", "photo", "application/octet-stream", bytes));
        if (caption != null) {
            request.param("caption", caption);
        }
        return request;
    }

    private static String photoId(ResultActions result) throws Exception {
        String body = result.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String id = JsonPath.read(body, "$.id");
        assertThat((String) JsonPath.read(body, "$.url")).isEqualTo("http://localhost:8081" + filePath(id));
        return id;
    }

    private void assertSize(String id, int width, int height) throws Exception {
        byte[] served = mockMvc.perform(get(filePath(id)))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_JPEG))
                .andReturn().getResponse().getContentAsByteArray();
        BufferedImage stored = ImageIO.read(new ByteArrayInputStream(served));
        assertThat(stored.getWidth()).isEqualTo(width);
        assertThat(stored.getHeight()).isEqualTo(height);
    }

    private static String filePath(String id) {
        return "/api/public/files/track-photos/" + id + ".jpg";
    }

    private static byte[] image(String format, int width, int height) throws Exception {
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        assertThat(ImageIO.write(img, format, out)).isTrue();
        return out.toByteArray();
    }
}
