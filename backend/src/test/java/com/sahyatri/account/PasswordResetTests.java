package com.sahyatri.account;

import com.sahyatri.auth.AuthTestSupport;
import com.sahyatri.common.util.HashingUtils;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PasswordResetTests extends AuthTestSupport {

    @Test
    void resetsPasswordAndSignsIn() throws Exception {
        String email = uniqueEmail();
        emailTrekker(email);

        forgot(email.toUpperCase())
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.expires_in").value(1800));
        String link = lastLink(email);
        assertThat(link).startsWith("http://localhost:5173/reset-password?token=");

        MvcResult result = reset(tokenOf(link), "newpass123")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.email").value(email))
                .andExpect(jsonPath("$.user.email_verified").value(true))
                .andReturn();
        assertThat(refreshToken(result)).isNotBlank();

        login(email, "trekking1").andExpect(status().isUnauthorized());
        login(email, "newpass123").andExpect(status().isOk());
        // The sign-up session was revoked; only the reset session and the new login remain.
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM refresh_tokens
                WHERE user_id = ? AND revoked_at IS NULL""", Integer.class, userIdByEmail(email))).isEqualTo(2);
    }

    @Test
    void linksAreSingleUseAndOnlyTheNewestCounts() throws Exception {
        String email = uniqueEmail();
        emailTrekker(email);

        forgot(email).andExpect(status().isAccepted());
        String first = tokenOf(lastLink(email));
        forgot(email).andExpect(status().isAccepted());
        String second = tokenOf(lastLink(email));

        reset(first, "newpass123")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PASSWORD_RESET_INVALID"));
        reset(second, "newpass123").andExpect(status().isOk());
        reset(second, "another123")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PASSWORD_RESET_INVALID"));
    }

    @Test
    void unknownEmailLooksTheSameAndSendsNothing() throws Exception {
        forgot(uniqueEmail())
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.expires_in").value(1800));
        verify(emailSender, never()).sendPasswordResetLink(anyString(), anyString());
    }

    @Test
    void requestsOverTheHourlyLimitAreSilentlyDropped() throws Exception {
        String email = uniqueEmail();
        emailTrekker(email);
        for (int i = 0; i < 4; i++) {
            forgot(email).andExpect(status().isAccepted());
        }
        verify(emailSender, times(3)).sendPasswordResetLink(eq(email), anyString());
    }

    @Test
    void expiredAndUnknownLinksAreRejected() throws Exception {
        String email = uniqueEmail();
        emailTrekker(email);
        forgot(email).andExpect(status().isAccepted());
        String token = tokenOf(lastLink(email));
        jdbc.update("UPDATE password_resets SET expires_at = now() - interval '1 second' WHERE token_hash = ?",
                HashingUtils.sha256Hex(token));

        reset(token, "newpass123")
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.code").value("PASSWORD_RESET_EXPIRED"));
        reset("not-a-real-token", "newpass123")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PASSWORD_RESET_INVALID"));
    }

    @Test
    void linkDiesIfTheEmailChangesFirst() throws Exception {
        String oldEmail = uniqueEmail();
        String access = emailTrekker(oldEmail);
        forgot(oldEmail).andExpect(status().isAccepted());
        String token = tokenOf(lastLink(oldEmail));

        String newEmail = uniqueEmail();
        authed(post("/api/account/email"), access, """
                {"email":"%s"}""".formatted(newEmail)).andExpect(status().isAccepted());
        ArgumentCaptor<String> verifyLink = ArgumentCaptor.forClass(String.class);
        verify(emailSender).sendVerificationLink(eq(newEmail), verifyLink.capture());
        postJson("/api/auth/email/verify", """
                {"token":"%s"}""".formatted(tokenOf(verifyLink.getValue()))).andExpect(status().isOk());

        reset(token, "newpass123")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PASSWORD_RESET_INVALID"));
    }

    @Test
    void validatesInput() throws Exception {
        forgot("nope")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.fields.email").exists());
        reset("some-token", "short")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.fields.new_password").exists());
    }

    private ResultActions forgot(String email) throws Exception {
        return postJson("/api/auth/password/forgot", """
                {"email":"%s"}""".formatted(email));
    }

    private ResultActions reset(String token, String password) throws Exception {
        return postJson("/api/auth/password/reset", """
                {"token":"%s","new_password":"%s"}""".formatted(token, password));
    }

    private String lastLink(String to) {
        ArgumentCaptor<String> link = ArgumentCaptor.forClass(String.class);
        verify(emailSender, atLeastOnce()).sendPasswordResetLink(eq(to), link.capture());
        return link.getValue();
    }

    private static String tokenOf(String link) {
        return link.substring(link.indexOf("token=") + "token=".length());
    }
}
