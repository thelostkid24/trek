package com.sahyatri.account;

import com.sahyatri.auth.AuthTestSupport;
import com.sahyatri.common.util.HashingUtils;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.web.servlet.ResultActions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class EmailChangeTests extends AuthTestSupport {

    @Test
    void verifiesCurrentUnverifiedEmail() throws Exception {
        String email = uniqueEmail();
        String token = emailTrekker(email);

        requestChange(token, email.toUpperCase())
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.expires_in").value(86400));
        String link = lastLink(email);
        assertThat(link).startsWith("http://localhost:5173/account/verify-email?token=");

        verifyToken(tokenOf(link))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email));
        authed(get("/api/auth/me"), token, null)
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.email_verified").value(true));
        verify(emailSender, never()).sendEmailChangedNotice(anyString(), anyString());

        requestChange(token, email)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_VERIFIED"));
    }

    @Test
    void changesEmailOnlyAfterLinkIsOpened() throws Exception {
        String oldEmail = uniqueEmail();
        String newEmail = uniqueEmail();
        String token = emailTrekker(oldEmail);

        requestChange(token, newEmail).andExpect(status().isAccepted());
        authed(get("/api/auth/me"), token, null).andExpect(jsonPath("$.email").value(oldEmail));

        String verifyToken = tokenOf(lastLink(newEmail));
        verifyToken(verifyToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(newEmail));
        authed(get("/api/auth/me"), token, null)
                .andExpect(jsonPath("$.email").value(newEmail))
                .andExpect(jsonPath("$.email_verified").value(true));
        verify(emailSender).sendEmailChangedNotice(oldEmail, newEmail);

        // Password sign-in now uses the new address.
        postJson("/api/auth/login", """
                {"email":"%s","password":"trekking1"}""".formatted(newEmail)).andExpect(status().isOk());

        // Links are single use.
        verifyToken(verifyToken)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("EMAIL_TOKEN_INVALID"));
    }

    @Test
    void phoneOnlyAccountCanAddEmail() throws Exception {
        String token = accessToken(phoneTrekker(uniquePhone()));
        String email = uniqueEmail();

        requestChange(token, email).andExpect(status().isAccepted());
        verifyToken(tokenOf(lastLink(email))).andExpect(status().isOk());

        authed(get("/api/auth/me"), token, null)
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.email_verified").value(true));
        verify(emailSender, never()).sendEmailChangedNotice(anyString(), anyString());
    }

    @Test
    void newerRequestSupersedesOlderLink() throws Exception {
        String token = emailTrekker(uniqueEmail());
        String first = uniqueEmail();
        String second = uniqueEmail();

        requestChange(token, first).andExpect(status().isAccepted());
        String firstToken = tokenOf(lastLink(first));
        requestChange(token, second).andExpect(status().isAccepted());

        verifyToken(firstToken)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("EMAIL_TOKEN_INVALID"));
        verifyToken(tokenOf(lastLink(second))).andExpect(status().isOk());
    }

    @Test
    void expiredAndUnknownLinksAreRejected() throws Exception {
        String token = emailTrekker(uniqueEmail());
        String email = uniqueEmail();
        requestChange(token, email).andExpect(status().isAccepted());
        String verifyToken = tokenOf(lastLink(email));
        jdbc.update("UPDATE email_verifications SET expires_at = now() - interval '1 second' WHERE token_hash = ?",
                HashingUtils.sha256Hex(verifyToken));

        verifyToken(verifyToken)
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.code").value("EMAIL_TOKEN_EXPIRED"));
        verifyToken("not-a-real-token")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("EMAIL_TOKEN_INVALID"));
        postJson("/api/auth/email/verify", "{}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void takenEmailIsRejectedAtRequestAndAtVerify() throws Exception {
        String taken = uniqueEmail();
        emailTrekker(taken);
        String token = emailTrekker(uniqueEmail());

        requestChange(token, taken)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_REGISTERED"));

        // Someone else claims the address between request and verify.
        String contested = uniqueEmail();
        requestChange(token, contested).andExpect(status().isAccepted());
        String verifyToken = tokenOf(lastLink(contested));
        emailTrekker(contested);
        verifyToken(verifyToken)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_REGISTERED"));
    }

    @Test
    void requestsAreRateLimitedPerUser() throws Exception {
        String token = emailTrekker(uniqueEmail());
        for (int i = 0; i < 5; i++) {
            requestChange(token, uniqueEmail()).andExpect(status().isAccepted());
        }
        requestChange(token, uniqueEmail())
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("EMAIL_RATE_LIMITED"))
                .andExpect(jsonPath("$.details.retry_after").isNumber());
    }

    @Test
    void requestRequiresSignInAndValidEmail() throws Exception {
        postJson("/api/account/email", """
                {"email":"%s"}""".formatted(uniqueEmail()))
                .andExpect(status().isUnauthorized());
        requestChange(emailTrekker(uniqueEmail()), "nope")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.fields.email").exists());
    }

    private ResultActions requestChange(String token, String email) throws Exception {
        return authed(post("/api/account/email"), token, """
                {"email":"%s"}""".formatted(email));
    }

    private ResultActions verifyToken(String token) throws Exception {
        return postJson("/api/auth/email/verify", """
                {"token":"%s"}""".formatted(token));
    }

    private String lastLink(String to) {
        ArgumentCaptor<String> link = ArgumentCaptor.forClass(String.class);
        verify(emailSender, atLeastOnce()).sendVerificationLink(eq(to), link.capture());
        return link.getValue();
    }

    private static String tokenOf(String link) {
        return link.substring(link.indexOf("token=") + "token=".length());
    }
}
