package com.sahyatri.guide;

import com.jayway.jsonpath.JsonPath;
import com.sahyatri.auth.AuthTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockMultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.UUID;

import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Admin-set guide name, home city, bio and photo (docs/TRD.md §7.12). */
class GuideProfileTests extends AuthTestSupport {

    @Test
    void adminSetsWhatTheGuidePageShows() throws Exception {
        String admin = adminToken();
        UUID guide = guideUser();
        String profile = "/api/admin/guides/" + guide + "/profile";
        String bio = "Grew up among deodar forests.\n\n" + "I have led 21 treks. ".repeat(60);

        authed(put(profile), admin, """
                {"full_name":"  Shray Rai ","home_city":"Chakrata, Dehradun","bio":"%s"}"""
                .formatted(bio.replace("\n", "\\n")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.full_name").value("Shray Rai"))
                .andExpect(jsonPath("$.home_city").value("Chakrata, Dehradun"))
                .andExpect(jsonPath("$.avatar_url").value(nullValue()));
        authed(get(profile), admin, null).andExpect(jsonPath("$.bio").value(bio.trim()));

        mockMvc.perform(get("/api/public/guides/" + guide))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.full_name").value("Shray Rai"))
                .andExpect(jsonPath("$.home_city").value("Chakrata, Dehradun"))
                .andExpect(jsonPath("$.bio").value(bio.trim()));

        String body = authed(multipart(HttpMethod.PUT, "/api/admin/guides/" + guide + "/avatar")
                .file(new MockMultipartFile("file", "shray.png", "image/png", png())), admin, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.avatar_url", startsWith("http")))
                .andReturn().getResponse().getContentAsString();
        String url = JsonPath.read(body, "$.avatar_url");
        mockMvc.perform(get("/api/public/guides/" + guide)).andExpect(jsonPath("$.avatar_url").value(url));

        authed(delete("/api/admin/guides/" + guide + "/avatar"), admin, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.avatar_url").value(nullValue()));

        // Blank clears the optional fields.
        authed(put(profile), admin, """
                {"full_name":"Shray Rai","home_city":" ","bio":""}""")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.home_city").value(nullValue()))
                .andExpect(jsonPath("$.bio").value(nullValue()));
    }

    @Test
    void validatesAndOnlyTouchesGuides() throws Exception {
        String admin = adminToken();
        UUID guide = guideUser();
        String profile = "/api/admin/guides/" + guide + "/profile";

        authed(put(profile), admin, """
                {"full_name":" ","bio":"%s"}""".formatted("x".repeat(2001)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.fields.full_name").exists())
                .andExpect(jsonPath("$.details.fields.bio").exists());

        String trekkerEmail = uniqueEmail();
        emailTrekker(trekkerEmail);
        authed(put("/api/admin/guides/" + userIdByEmail(trekkerEmail) + "/profile"), admin, """
                {"full_name":"Not a guide"}""")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("GUIDE_NOT_FOUND"));

        authed(put(profile), emailTrekker(uniqueEmail()), """
                {"full_name":"Sneaky"}""")
                .andExpect(status().isForbidden());
    }

    private static byte[] png() throws Exception {
        BufferedImage img = new BufferedImage(400, 500, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, "png", out);
        return out.toByteArray();
    }
}
