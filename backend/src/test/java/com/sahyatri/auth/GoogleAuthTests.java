package com.sahyatri.auth;

import com.sahyatri.auth.dto.GoogleIdentity;
import com.sahyatri.common.exception.ApiException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.ResultActions;

import java.util.UUID;

import static org.mockito.Mockito.when;
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
        String token = stubToken(new GoogleIdentity("g-" + UUID.randomUUID(), email, true, "Someone Else"));

        google(token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.is_new_user").value(false))
                .andExpect(jsonPath("$.user.id").value(userId))
                .andExpect(jsonPath("$.user.full_name").value("Asha Rao"))
                .andExpect(jsonPath("$.user.email_verified").value(true))
                .andExpect(jsonPath("$.user.auth_methods.length()").value(2));
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
