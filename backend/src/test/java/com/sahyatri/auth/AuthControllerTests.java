package com.sahyatri.auth;

import com.sahyatri.auth.service.LoginAttemptLimiter;
import com.sahyatri.common.util.HashingUtils;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthControllerTests extends AuthTestSupport {

    @Autowired
    JwtEncoder jwtEncoder;

    @Test
    void signupCreatesTrekkerAndSetsRefreshCookie() throws Exception {
        String email = uniqueEmail();
        signup(email.toUpperCase(), "trekking1")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.token_type").value("Bearer"))
                .andExpect(jsonPath("$.expires_in").value(900))
                .andExpect(jsonPath("$.is_new_user").value(true))
                .andExpect(jsonPath("$.access_token").isString())
                .andExpect(jsonPath("$.user.email").value(email))
                .andExpect(jsonPath("$.user.full_name").value("Asha Rao"))
                .andExpect(jsonPath("$.user.role").value("TREKKER"))
                .andExpect(jsonPath("$.user.email_verified").value(false))
                .andExpect(jsonPath("$.user.auth_methods[0]").value("PASSWORD"))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("HttpOnly")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Path=/api/auth")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("SameSite=Lax")));
    }

    @Test
    void signupWithTakenEmailConflicts() throws Exception {
        String email = uniqueEmail();
        signup(email, "trekking1").andExpect(status().isCreated());
        signup(email, "trekking2")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_REGISTERED"));
    }

    @Test
    void signupValidationReportsSnakeCaseFields() throws Exception {
        postJson("/api/auth/signup", """
                {"full_name":"","email":"not-an-email","password":"short"}""")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.details.fields.full_name").exists())
                .andExpect(jsonPath("$.details.fields.email").exists())
                .andExpect(jsonPath("$.details.fields.password").exists());
    }

    @Test
    void loginSucceedsWithRightPasswordOnly() throws Exception {
        String email = uniqueEmail();
        signup(email, "trekking1");

        postJson("/api/auth/login", """
                {"email":"%s","password":"trekking1"}""".formatted(email))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.is_new_user").value(false))
                .andExpect(jsonPath("$.user.email").value(email));

        postJson("/api/auth/login", """
                {"email":"%s","password":"wrongpass1"}""".formatted(email))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));

        postJson("/api/auth/login", """
                {"email":"%s","password":"trekking1"}""".formatted(uniqueEmail()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
    }

    @Test
    void repeatedLoginFailuresAreThrottled() throws Exception {
        String email = uniqueEmail();
        signup(email, "trekking1");
        String wrong = """
                {"email":"%s","password":"wrongpass1"}""".formatted(email);
        for (int i = 0; i < LoginAttemptLimiter.MAX_FAILURES; i++) {
            postJson("/api/auth/login", wrong).andExpect(status().isUnauthorized());
        }
        postJson("/api/auth/login", """
                {"email":"%s","password":"trekking1"}""".formatted(email))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("TOO_MANY_ATTEMPTS"));
    }

    @Test
    void meRequiresValidBearerToken() throws Exception {
        String email = uniqueEmail();
        String token = accessToken(signup(email, "trekking1").andReturn());

        mockMvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email));

        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));

        mockMvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer garbage"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void expiredAccessTokenReportsTokenExpired() throws Exception {
        Instant past = Instant.now().minus(Duration.ofHours(1));
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(UUID.randomUUID().toString())
                .issuedAt(past.minus(Duration.ofMinutes(15)))
                .expiresAt(past)
                .claim("role", "TREKKER")
                .build();
        String expired = jwtEncoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();

        mockMvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + expired))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("TOKEN_EXPIRED"));
    }

    @Test
    void roleGuardsRejectWrongRole() throws Exception {
        String token = accessToken(signup(uniqueEmail(), "trekking1").andReturn());
        mockMvc.perform(get("/api/admin/anything").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void refreshRotatesAndDetectsReuse() throws Exception {
        MvcResult signedUp = signup(uniqueEmail(), "trekking1").andReturn();
        String first = refreshToken(signedUp);

        MvcResult refreshed = mockMvc.perform(post("/api/auth/refresh").cookie(refreshCookie(first)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.access_token").isString())
                .andExpect(jsonPath("$.is_new_user").value(false))
                .andReturn();
        String second = refreshToken(refreshed);
        assertThat(second).isNotEqualTo(first);

        // Second tab racing with the first: the just-rotated token still gets a fresh sibling.
        mockMvc.perform(post("/api/auth/refresh").cookie(refreshCookie(first)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/auth/refresh").cookie(refreshCookie(second)))
                .andExpect(status().isOk());
    }

    @Test
    void reusingLongRevokedRefreshTokenRevokesWholeSession() throws Exception {
        String first = refreshToken(signup(uniqueEmail(), "trekking1").andReturn());
        String second = refreshToken(mockMvc.perform(post("/api/auth/refresh").cookie(refreshCookie(first))).andReturn());
        jdbc.update("UPDATE refresh_tokens SET revoked_at = now() - interval '1 hour' WHERE token_hash = ?",
                HashingUtils.sha256Hex(first));

        mockMvc.perform(post("/api/auth/refresh").cookie(refreshCookie(first)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("REFRESH_TOKEN_INVALID"));
        mockMvc.perform(post("/api/auth/refresh").cookie(refreshCookie(second)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void refreshWithoutOrWithUnknownCookieFails() throws Exception {
        mockMvc.perform(post("/api/auth/refresh"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("REFRESH_TOKEN_INVALID"));
        mockMvc.perform(post("/api/auth/refresh").cookie(refreshCookie("nope")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("REFRESH_TOKEN_INVALID"));
    }

    @Test
    void logoutRevokesRefreshTokenAndClearsCookie() throws Exception {
        String refresh = refreshToken(signup(uniqueEmail(), "trekking1").andReturn());

        mockMvc.perform(post("/api/auth/logout").cookie(refreshCookie(refresh)))
                .andExpect(status().isNoContent())
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Max-Age=0")));

        mockMvc.perform(post("/api/auth/refresh").cookie(refreshCookie(refresh)))
                .andExpect(status().isUnauthorized());
    }
}
