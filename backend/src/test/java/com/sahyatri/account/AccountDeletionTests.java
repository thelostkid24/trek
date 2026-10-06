package com.sahyatri.account;

import com.sahyatri.auth.AuthTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AccountDeletionTests extends AuthTestSupport {

    @Test
    void deletingErasesThePersonAndSignsOutEverywhere() throws Exception {
        String email = uniqueEmail();
        MvcResult signedUp = signup(email, "trekking1").andReturn();
        String token = accessToken(signedUp);
        UUID id = userIdByEmail(email);
        jdbc.update("""
                INSERT INTO trekker_profiles (user_id, medical_notes, created_at, updated_at)
                VALUES (?, 'Mild asthma', now(), now())""", id);
        jdbc.update("UPDATE users SET marketing_email_consent_at = now(), gclid = 'abc', utm_source = 'instagram' WHERE id = ?", id);
        authed(multipart(HttpMethod.PUT, "/api/account/avatar")
                .file(new MockMultipartFile("file", "me.png", "image/png", png())), token, null)
                .andExpect(status().isOk());
        String avatarKey = jdbc.queryForObject("SELECT avatar_key FROM users WHERE id = ?", String.class, id);
        assertThat(Files.exists(UPLOAD_DIR.resolve("avatars").resolve(avatarKey + ".jpg"))).isTrue();

        delete(token, "{\"password\":\"wrongpass1\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CURRENT_PASSWORD_INCORRECT"));
        delete(token, null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CURRENT_PASSWORD_INCORRECT"));

        delete(token, "{\"password\":\"trekking1\"}")
                .andExpect(status().isNoContent())
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Max-Age=0")));

        Map<String, Object> row = jdbc.queryForMap("SELECT * FROM users WHERE id = ?", id);
        assertThat(row.get("status")).isEqualTo("DELETED");
        assertThat(row.get("deleted_at")).isNotNull();
        for (String erased : new String[]{"full_name", "email", "phone", "password_hash", "google_subject", "avatar_key",
                "email_verified_at", "marketing_email_consent_at", "gclid"}) {
            assertThat(row.get(erased)).as(erased).isNull();
        }
        assertThat(row.get("utm_source")).isEqualTo("instagram");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM trekker_profiles WHERE user_id = ?", Integer.class, id)).isZero();
        assertThat(Files.exists(UPLOAD_DIR.resolve("avatars").resolve(avatarKey + ".jpg"))).isFalse();
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM audit_events WHERE action = 'ACCOUNT_DELETED' AND entity_id = ?", Integer.class, id))
                .isEqualTo(1);

        // Signed out everywhere, and the email is free for a new account.
        authed(get("/api/auth/me"), token, null).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/auth/refresh").cookie(refreshCookie(refreshToken(signedUp))))
                .andExpect(status().isUnauthorized());
        login(email, "trekking1").andExpect(status().isUnauthorized());
        signup(email, "trekking2").andExpect(status().isCreated());
    }

    @Test
    void accountsWithoutAPasswordNeedNone() throws Exception {
        String token = accessToken(phoneTrekker(uniquePhone()));
        delete(token, "{}").andExpect(status().isNoContent());
        authed(get("/api/auth/me"), token, null).andExpect(status().isUnauthorized());
    }

    @Test
    void notWhileAPaidTrekIsAheadAndBookingRecordsAreKeptWithoutNames() throws Exception {
        String token = bookingTrekker();
        UUID booking = confirmedBooking(token, publishedDeparture(), 2);

        delete(token, "{\"password\":\"trekking1\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("UPCOMING_TRIP"));

        // Once the trek is over, deleting works and the paid booking stays (law 1), minus names and contacts.
        jdbc.update("""
                UPDATE departures SET start_date = start_date - 60, end_date = end_date - 60
                WHERE id = (SELECT departure_id FROM bookings WHERE id = ?)""", booking);
        delete(token, "{\"password\":\"trekking1\"}").andExpect(status().isNoContent());

        Map<String, Object> kept = jdbc.queryForMap("SELECT * FROM bookings WHERE id = ?", booking);
        assertThat(kept.get("status")).isEqualTo("CONFIRMED");
        assertThat(kept.get("contact_name")).isNull();
        assertThat(kept.get("contact_email")).isNull();
        assertThat(kept.get("contact_phone")).isNull();
        assertThat(jdbc.queryForList("SELECT full_name FROM booking_travellers WHERE booking_id = ?", String.class, booking))
                .containsOnly("Removed");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM payments WHERE booking_id = ? AND status = 'PAID'",
                Integer.class, booking)).isEqualTo(1);
    }

    @Test
    void guidesAndAdminsAreClosedByTheTeam() throws Exception {
        String admin = adminToken();
        delete(admin, "{\"password\":\"trekking1\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ACCOUNT_DELETION_UNAVAILABLE"));
        authed(get("/api/auth/me"), admin, null).andExpect(status().isOk());
    }

    @Test
    void wrongPasswordIsThrottled() throws Exception {
        String token = emailTrekker(uniqueEmail());
        for (int i = 0; i < 5; i++) {
            delete(token, "{\"password\":\"wrongpass1\"}").andExpect(status().isBadRequest());
        }
        delete(token, "{\"password\":\"trekking1\"}")
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("TOO_MANY_ATTEMPTS"));
    }

    private ResultActions delete(String token, String json) throws Exception {
        return authed(post("/api/account/delete"), token, json);
    }

    private static byte[] png() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(64, 64, BufferedImage.TYPE_INT_RGB), "png", out);
        return out.toByteArray();
    }
}
