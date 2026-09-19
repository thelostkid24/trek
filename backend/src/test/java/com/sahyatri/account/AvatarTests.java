package com.sahyatri.account;

import com.jayway.jsonpath.JsonPath;
import com.sahyatri.auth.AuthTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.ResultActions;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AvatarTests extends AuthTestSupport {

    private static final String AVATAR = "/api/account/avatar";

    @Test
    void uploadStoresSquareJpegAndReplacesPrevious() throws Exception {
        String token = emailTrekker(uniqueEmail());

        String firstUrl = avatarUrl(upload(token, image("png", 800, 600)));
        assertThat(firstUrl).startsWith("http://localhost:8081/api/public/files/avatars/").endsWith(".jpg");

        byte[] served = mockMvc.perform(get(path(firstUrl)))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_JPEG))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, containsString("immutable")))
                .andReturn().getResponse().getContentAsByteArray();
        BufferedImage stored = ImageIO.read(new ByteArrayInputStream(served));
        assertThat(stored.getWidth()).isEqualTo(512);
        assertThat(stored.getHeight()).isEqualTo(512);

        authed(get("/api/auth/me"), token, null).andExpect(jsonPath("$.avatar_url").value(firstUrl));
        authed(get("/api/trekker/profile"), token, null).andExpect(jsonPath("$.avatar_url").value(firstUrl));

        String secondUrl = avatarUrl(upload(token, image("jpg", 300, 900)));
        assertThat(secondUrl).isNotEqualTo(firstUrl);
        mockMvc.perform(get(path(firstUrl))).andExpect(status().isNotFound());
        assertThat(storedFile(firstUrl)).doesNotExist();
        mockMvc.perform(get(path(secondUrl))).andExpect(status().isOk());
    }

    @Test
    void storedJpegCarriesNoMetadataFromTheUpload() throws Exception {
        String token = emailTrekker(uniqueEmail());
        byte[] jpeg = image("jpg", 600, 600);
        // Splice a fake EXIF APP1 segment right after the SOI marker.
        byte[] exif = "Exif\0\0GPS-SECRET-LOCATION".getBytes(StandardCharsets.ISO_8859_1);
        ByteArrayOutputStream withExif = new ByteArrayOutputStream();
        withExif.write(jpeg, 0, 2);
        withExif.write(new byte[] {(byte) 0xFF, (byte) 0xE1, 0, (byte) (exif.length + 2)});
        withExif.write(exif);
        withExif.write(jpeg, 2, jpeg.length - 2);

        String url = avatarUrl(upload(token, withExif.toByteArray()));
        byte[] served = mockMvc.perform(get(path(url))).andReturn().getResponse().getContentAsByteArray();
        assertThat(new String(served, StandardCharsets.ISO_8859_1)).doesNotContain("GPS-SECRET-LOCATION");
    }

    @Test
    void rejectsNonImagesAndOtherFormats() throws Exception {
        String token = emailTrekker(uniqueEmail());

        upload(token, "just some text".getBytes(StandardCharsets.UTF_8))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_IMAGE"));
        upload(token, image("gif", 50, 50))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_IMAGE"));
        // PNG signature followed by garbage.
        byte[] fakePng = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n', 1, 2, 3, 4};
        upload(token, fakePng)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_IMAGE"));
    }

    @Test
    void rejectsFilesOver5Mb() throws Exception {
        String token = emailTrekker(uniqueEmail());
        byte[] big = new byte[5 * 1024 * 1024 + 1];
        byte[] png = image("png", 10, 10);
        System.arraycopy(png, 0, big, 0, png.length);

        upload(token, big)
                .andExpect(status().isContentTooLarge())
                .andExpect(jsonPath("$.code").value("FILE_TOO_LARGE"));
    }

    @Test
    void missingFilePartIsAValidationError() throws Exception {
        String token = emailTrekker(uniqueEmail());
        authed(multipart(HttpMethod.PUT, AVATAR), token, null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.details.fields.file").exists());
    }

    @Test
    void deleteRemovesPhoto() throws Exception {
        String token = emailTrekker(uniqueEmail());
        String url = avatarUrl(upload(token, image("png", 100, 100)));

        authed(delete(AVATAR), token, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.avatar_url").value(nullValue()));
        mockMvc.perform(get(path(url))).andExpect(status().isNotFound());

        // Idempotent.
        authed(delete(AVATAR), token, null).andExpect(status().isOk());
    }

    @Test
    void requiresSignInAndServesOnlyUuidKeys() throws Exception {
        mockMvc.perform(multipart(HttpMethod.PUT, AVATAR)
                        .file(new MockMultipartFile("file", "a.png", "image/png", image("png", 10, 10))))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/public/files/avatars/..%2F..%2Fetc%2Fpasswd.jpg"))
                .andExpect(status().is4xxClientError());
        mockMvc.perform(get("/api/public/files/avatars/00000000-0000-0000-0000-000000000000.jpg"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    private ResultActions upload(String token, byte[] bytes) throws Exception {
        return authed(multipart(HttpMethod.PUT, AVATAR)
                .file(new MockMultipartFile("file", "photo", "application/octet-stream", bytes)), token, null);
    }

    private static String avatarUrl(ResultActions result) throws Exception {
        String body = result
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.avatar_url", startsWith("http")))
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.avatar_url");
    }

    private static String path(String url) {
        return url.substring("http://localhost:8081".length());
    }

    private static Path storedFile(String url) {
        return UPLOAD_DIR.resolve("avatars").resolve(url.substring(url.lastIndexOf('/') + 1));
    }

    private static byte[] image(String format, int width, int height) throws Exception {
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                img.setRGB(x, y, (x * 7 + y * 3) & 0xFFFFFF);
            }
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        assertThat(ImageIO.write(img, format, out)).isTrue();
        return out.toByteArray();
    }
}
