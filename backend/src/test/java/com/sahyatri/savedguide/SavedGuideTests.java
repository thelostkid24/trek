package com.sahyatri.savedguide;

import com.sahyatri.auth.AuthTestSupport;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** A trekker's saved guides, "My Fav" (docs/TRD.md §7.21.7). */
class SavedGuideTests extends AuthTestSupport {

    @Test
    void saveAndUnsaveAreIdempotentAndTheListShowsTheGuide() throws Exception {
        String trekker = emailTrekker(uniqueEmail());
        String firstEmail = uniqueEmail();
        String firstToken = tokenWithRole(firstEmail, "GUIDE");
        UUID first = userIdByEmail(firstEmail);
        UUID second = guideUser();
        // Opening their own profile gives the guide a slug.
        authed(get("/api/guide/profile"), firstToken, null).andExpect(status().isOk());
        jdbc.update("UPDATE users SET full_name = 'Shrey Rai' WHERE id = ?", first);
        jdbc.update("""
                INSERT INTO trekker_profiles (user_id, home_city, created_at, updated_at)
                VALUES (?, 'Sankri', now(), now())""", first);

        authed(get("/api/trekker/saved-guides"), trekker, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(0)));

        authed(put("/api/trekker/saved-guides/" + first), trekker, null).andExpect(status().isNoContent());
        authed(put("/api/trekker/saved-guides/" + first), trekker, null).andExpect(status().isNoContent());
        authed(put("/api/trekker/saved-guides/" + second), trekker, null).andExpect(status().isNoContent());
        assertThat(saves(first)).isEqualTo(1);

        // Newest first.
        authed(get("/api/trekker/saved-guides"), trekker, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(2)))
                .andExpect(jsonPath("$.items[0].guide_id").value(second.toString()))
                .andExpect(jsonPath("$.items[1].guide_id").value(first.toString()))
                .andExpect(jsonPath("$.items[1].full_name").value("Shrey Rai"))
                .andExpect(jsonPath("$.items[1].home_city").value("Sankri"))
                .andExpect(jsonPath("$.items[1].slug").isString())
                .andExpect(jsonPath("$.items[1].review_count").value(0))
                .andExpect(jsonPath("$.items[1].saved_at").isString());

        authed(delete("/api/trekker/saved-guides/" + second), trekker, null).andExpect(status().isNoContent());
        authed(delete("/api/trekker/saved-guides/" + second), trekker, null).andExpect(status().isNoContent());
        authed(get("/api/trekker/saved-guides"), trekker, null)
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].guide_id").value(first.toString()));

        // Another trekker's list is their own.
        authed(get("/api/trekker/saved-guides"), emailTrekker(uniqueEmail()), null)
                .andExpect(jsonPath("$.items", hasSize(0)));

        // A disabled guide drops off the list.
        jdbc.update("UPDATE users SET status = 'DISABLED' WHERE id = ?", first);
        authed(get("/api/trekker/saved-guides"), trekker, null).andExpect(jsonPath("$.items", hasSize(0)));
    }

    @Test
    void onlyGuidesCanBeSaved() throws Exception {
        String trekkerEmail = uniqueEmail();
        String trekker = emailTrekker(trekkerEmail);
        UUID otherTrekker = userIdByEmail(uniqueEmailTrekker());

        for (UUID id : new UUID[] {otherTrekker, UUID.randomUUID(), userIdByEmail(trekkerEmail)}) {
            authed(put("/api/trekker/saved-guides/" + id), trekker, null)
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("GUIDE_NOT_FOUND"));
        }
        UUID disabled = guideUser();
        jdbc.update("UPDATE users SET status = 'DISABLED' WHERE id = ?", disabled);
        authed(put("/api/trekker/saved-guides/" + disabled), trekker, null).andExpect(status().isNotFound());
    }

    @Test
    void onlyTrekkersHaveAList() throws Exception {
        UUID guide = guideUser();
        String guideToken = tokenWithRole(uniqueEmail(), "GUIDE");
        authed(get("/api/trekker/saved-guides"), guideToken, null).andExpect(status().isForbidden());
        authed(put("/api/trekker/saved-guides/" + guide), guideToken, null).andExpect(status().isForbidden());
        authed(put("/api/trekker/saved-guides/" + guide), adminToken(), null).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/trekker/saved-guides")).andExpect(status().isUnauthorized());
        mockMvc.perform(put("/api/trekker/saved-guides/" + guide)).andExpect(status().isUnauthorized());
    }

    @Test
    void deletingTheAccountClearsItsSaves() throws Exception {
        String email = uniqueEmail();
        String trekker = emailTrekker(email);
        UUID guide = guideUser();
        authed(put("/api/trekker/saved-guides/" + guide), trekker, null).andExpect(status().isNoContent());
        assertThat(saves(guide)).isEqualTo(1);

        UUID id = userIdByEmail(email);
        authed(post("/api/account/delete"), trekker, """
                {"password":"trekking1"}""").andExpect(status().isNoContent());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM saved_guides WHERE user_id = ?", Integer.class, id))
                .isZero();
    }

    private String uniqueEmailTrekker() throws Exception {
        String email = uniqueEmail();
        emailTrekker(email);
        return email;
    }

    private int saves(UUID guideId) {
        return jdbc.queryForObject("SELECT count(*) FROM saved_guides WHERE guide_id = ?", Integer.class, guideId);
    }
}
