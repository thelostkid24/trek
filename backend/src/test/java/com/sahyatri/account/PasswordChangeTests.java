package com.sahyatri.account;

import com.sahyatri.auth.AuthTestSupport;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PasswordChangeTests extends AuthTestSupport {

    @Test
    void changeNeedsCurrentPasswordAndRevokesOtherSessions() throws Exception {
        String email = uniqueEmail();
        MvcResult signedUp = signup(email, "trekking1").andReturn();
        String token = accessToken(signedUp);
        String oldRefresh = refreshToken(signedUp);

        change(token, null, "newpass22")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CURRENT_PASSWORD_INCORRECT"));
        change(token, "wrongpass1", "newpass22")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CURRENT_PASSWORD_INCORRECT"));

        MvcResult changed = change(token, "trekking1", "newpass22")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.access_token").isString())
                .andExpect(jsonPath("$.is_new_user").value(false))
                .andExpect(jsonPath("$.user.email").value(email))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Path=/api/auth")))
                .andReturn();

        mockMvc.perform(post("/api/auth/refresh").cookie(refreshCookie(oldRefresh)))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/auth/refresh").cookie(refreshCookie(refreshToken(changed))))
                .andExpect(status().isOk());

        login(email, "trekking1").andExpect(status().isUnauthorized());
        login(email, "newpass22").andExpect(status().isOk());
    }

    @Test
    void wrongCurrentPasswordIsThrottled() throws Exception {
        String token = emailTrekker(uniqueEmail());
        for (int i = 0; i < 5; i++) {
            change(token, "wrongpass1", "newpass22").andExpect(status().isBadRequest());
        }
        change(token, "trekking1", "newpass22")
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("TOO_MANY_ATTEMPTS"));
    }

    @Test
    void phoneOnlyAccountNeedsEmailBeforeSettingPassword() throws Exception {
        String token = accessToken(phoneTrekker(uniquePhone()));

        change(token, null, "newpass22")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("EMAIL_REQUIRED"));

        // Once an email exists, a first password can be set without a current one.
        String email = uniqueEmail();
        jdbc.update("UPDATE users SET email = ? WHERE id = ?::uuid", email, userId(token));
        change(token, null, "newpass22")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.auth_methods", hasItem("PASSWORD")));
        login(email, "newpass22").andExpect(status().isOk());
    }

    @Test
    void newPasswordMustFollowTheRules() throws Exception {
        String token = emailTrekker(uniqueEmail());
        change(token, "trekking1", "short")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.details.fields.new_password").exists());
    }

    private ResultActions change(String token, String current, String next) throws Exception {
        String currentJson = current == null ? "" : "\"current_password\":\"%s\",".formatted(current);
        return authed(put("/api/account/password"), token, """
                {%s"new_password":"%s"}""".formatted(currentJson, next));
    }

    private String userId(String token) throws Exception {
        String body = authed(get("/api/auth/me"), token, null).andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }
}
