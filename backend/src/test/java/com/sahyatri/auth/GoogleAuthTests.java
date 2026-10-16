package com.sahyatri.auth;

import com.sahyatri.auth.dto.GoogleIdentity;
import com.sahyatri.common.exception.ApiException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GoogleAuthTests extends AuthTestSupport {

    @Test
    void newGoogleUserBecomesVerifiedTrekker() throws Exception {
        String email = uniqueEmail();
        String token = stubToken(new GoogleIdentity("g-" + UUID.randomUUID(), email, true, "Meera S"));

        google(token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.is_new_user").value(true))
                .andExpect(jsonPath("$.user.email").value(email))
                .andExpect(jsonPath("$.user.full_name").value("Meera S"))
                .andExpect(jsonPath("$.user.email_verified").value(true))
                .andExpect(jsonPath("$.user.role").value("TREKKER"))
                .andExpect(jsonPath("$.user.auth_methods[0]").value("GOOGLE"));

        google(token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.is_new_user").value(false));
    }

    @Test
    void verifiedGoogleEmailLinksToExistingPasswordAccount() throws Exception {
        String email = uniqueEmail();
        String userId = com.jayway.jsonpath.JsonPath.read(
                signup(email, "trekking1").andReturn().getResponse().getContentAsString(), "$.user.id");
        jdbc.update("UPDATE users SET email_verified_at = now() WHERE email = ?", email);
        String token = stubToken(new GoogleIdentity("g-" + UUID.randomUUID(), email, true, "Someone Else"));

        google(token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.is_new_user").value(false))
                .andExpect(jsonPath("$.user.id").value(userId))
                .andExpect(jsonPath("$.user.full_name").value("Asha Rao"))
                .andExpect(jsonPath("$.user.email_verified").value(true))
                .andExpect(jsonPath("$.user.auth_methods.length()").value(2));
        login(email, "trekking1").andExpect(status().isOk());
    }

    /** Pre-hijack: someone signs up with an email they don't own, then the real owner arrives through Google. */
    @Test
    void googleLinkToUnverifiedAccountDropsSquattersCredentials() throws Exception {
        String email = uniqueEmail();
        String phone = uniquePhone();
        MvcResult squatter = signup(email, "trekking1").andExpect(status().isCreated()).andReturn();
        String userId = com.jayway.jsonpath.JsonPath.read(squatter.getResponse().getContentAsString(), "$.user.id");
        jdbc.update("UPDATE users SET phone = ?, phone_verified_at = now() WHERE email = ?", phone, email);
        String token = stubToken(new GoogleIdentity("g-" + UUID.randomUUID(), email, true, "Real Owner"));

        google(token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.id").value(userId))
                .andExpect(jsonPath("$.user.email_verified").value(true))
                .andExpect(jsonPath("$.user.phone").doesNotExist())
                .andExpect(jsonPath("$.user.auth_methods.length()").value(1))
                .andExpect(jsonPath("$.user.auth_methods[0]").value("GOOGLE"));

        login(email, "trekking1").andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/auth/refresh").cookie(refreshCookie(refreshToken(squatter))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void emailOwnedByAnotherGoogleAccountIsNotRebound() throws Exception {
        String email = uniqueEmail();
        google(stubToken(new GoogleIdentity("g-" + UUID.randomUUID(), email, true, "First Owner")))
                .andExpect(status().isOk());

        google(stubToken(new GoogleIdentity("g-" + UUID.randomUUID(), email, true, "Recycled Address")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("GOOGLE_ACCOUNT_MISMATCH"));
    }

    @Test
    void unverifiedGoogleEmailDoesNotLink() throws Exception {
        String email = uniqueEmail();
        signup(email, "trekking1");
        String token = stubToken(new GoogleIdentity("g-" + UUID.randomUUID(), email, false, "Imposter"));

        google(token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.is_new_user").value(true))
                .andExpect(jsonPath("$.user.email").doesNotExist());
    }

    @Test
    void invalidGoogleTokenIsRejected() throws Exception {
        when(googleTokenVerifier.verify("bad")).thenThrow(
                new ApiException(HttpStatus.UNAUTHORIZED, "GOOGLE_TOKEN_INVALID", "nope"));
        google("bad")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("GOOGLE_TOKEN_INVALID"));
    }

    private String stubToken(GoogleIdentity identity) {
        String token = "id-token-" + UUID.randomUUID();
        when(googleTokenVerifier.verify(token)).thenReturn(identity);
        return token;
    }

    private ResultActions google(String idToken) throws Exception {
        return postJson("/api/auth/google", """
                {"id_token":"%s"}""".formatted(idToken));
    }
}
