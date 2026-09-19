package com.sahyatri.admin;

import com.sahyatri.admin.service.AdminBootstrap;
import com.sahyatri.auth.AuthTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AdminGuideTests extends AuthTestSupport {

    private static final String GUIDES = "/api/admin/guides";

    @Autowired
    AdminBootstrap bootstrap;

    @Test
    void promotesTrekkerToGuideOnce() throws Exception {
        String admin = adminToken();
        String email = uniqueEmail();
        emailTrekker(email);

        authed(post(GUIDES), admin, """
                {"email":"%s"}""".formatted(email.toUpperCase()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.full_name").value("Asha Rao"));
        authed(get(GUIDES), admin, null).andExpect(jsonPath("$.items[*].email", hasItem(email)));

        // The new role shows on the next sign-in.
        login(email, "trekking1").andExpect(jsonPath("$.user.role").value("GUIDE"));

        authed(post(GUIDES), admin, """
                {"email":"%s"}""".formatted(email))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ALREADY_GUIDE"));
    }

    @Test
    void promotionErrors() throws Exception {
        String admin = adminToken();
        authed(post(GUIDES), admin, """
                {"email":"%s"}""".formatted(uniqueEmail()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"));

        String otherAdmin = uniqueEmail();
        tokenWithRole(otherAdmin, "ADMIN");
        authed(post(GUIDES), admin, """
                {"email":"%s"}""".formatted(otherAdmin))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ROLE_NOT_PROMOTABLE"));

        authed(post(GUIDES), emailTrekker(uniqueEmail()), """
                {"email":"%s"}""".formatted(otherAdmin))
                .andExpect(status().isForbidden());
    }

    @Test
    void bootstrapPromotesListedAccounts() throws Exception {
        if (jdbc.queryForObject("SELECT count(*) FROM users WHERE email = ?", Integer.class, BOOTSTRAP_ADMIN_EMAIL) == 0) {
            signup(BOOTSTRAP_ADMIN_EMAIL, "trekking1").andExpect(status().isCreated());
        }
        jdbc.update("UPDATE users SET role = 'TREKKER' WHERE email = ?", BOOTSTRAP_ADMIN_EMAIL);

        bootstrap.run(null);

        login(BOOTSTRAP_ADMIN_EMAIL, "trekking1").andExpect(jsonPath("$.user.role").value("ADMIN"));
    }
}
