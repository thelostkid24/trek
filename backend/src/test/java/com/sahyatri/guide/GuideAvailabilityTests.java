package com.sahyatri.guide;

import com.jayway.jsonpath.JsonPath;
import com.sahyatri.auth.AuthTestSupport;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The days a guide marks as free, seen only by them and admins (docs/TRD.md §7.21.7). */
class GuideAvailabilityTests extends AuthTestSupport {

    @Test
    void guideAddsEditsAndRemovesRanges() throws Exception {
        String guide = tokenWithRole(uniqueEmail(), "GUIDE");
        LocalDate start = today().plusDays(20);

        UUID later = create(guide, start.plusDays(30), start.plusDays(35), null);
        String body = authed(post("/api/guide/availability"), guide, range(start, start.plusDays(6), "  Ladakh season "))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.start_date").value(start.toString()))
                .andExpect(jsonPath("$.end_date").value(start.plusDays(6).toString()))
                .andExpect(jsonPath("$.note").value("Ladakh season"))
                .andReturn().getResponse().getContentAsString();
        UUID first = UUID.fromString(JsonPath.read(body, "$.id"));

        authed(get("/api/guide/availability"), guide, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(2)))
                .andExpect(jsonPath("$.items[0].id").value(first.toString()))
                .andExpect(jsonPath("$.items[1].id").value(later.toString()));

        // A single day is a range; a blank note clears it.
        authed(put("/api/guide/availability/" + first), guide, range(start.plusDays(2), start.plusDays(2), " "))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.end_date").value(start.plusDays(2).toString()))
                .andExpect(jsonPath("$.note").value(nullValue()));

        authed(delete("/api/guide/availability/" + later), guide, null).andExpect(status().isNoContent());
        authed(delete("/api/guide/availability/" + later), guide, null)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("AVAILABILITY_NOT_FOUND"));
        authed(get("/api/guide/availability"), guide, null).andExpect(jsonPath("$.items", hasSize(1)));
    }

    @Test
    void rangesAreCheckedAndNeverOverlap() throws Exception {
        String guide = tokenWithRole(uniqueEmail(), "GUIDE");
        LocalDate start = today().plusDays(10);
        UUID existing = create(guide, start, start.plusDays(5), null);

        authed(post("/api/guide/availability"), guide, range(start.plusDays(3), start.plusDays(1), null))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.details.fields.end_date").exists());
        authed(post("/api/guide/availability"), guide, range(today().minusDays(5), today().minusDays(1), null))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.fields.end_date").exists());
        authed(post("/api/guide/availability"), guide, range(start, start, "x".repeat(121)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.fields.note").exists());
        authed(post("/api/guide/availability"), guide, """
                {"start_date":"%s"}""".formatted(start))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.fields.end_date").exists());

        // Sharing even one day is an overlap; touching is fine.
        authed(post("/api/guide/availability"), guide, range(start.plusDays(5), start.plusDays(9), null))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("AVAILABILITY_OVERLAP"))
                .andExpect(jsonPath("$.details.id").value(existing.toString()));
        authed(post("/api/guide/availability"), guide, range(start.minusDays(2), start.plusDays(9), null))
                .andExpect(status().isConflict());
        UUID next = create(guide, start.plusDays(6), start.plusDays(9), null);

        // Moving a range onto another is an overlap; resizing it within its own days isn't.
        authed(put("/api/guide/availability/" + next), guide, range(start.plusDays(4), start.plusDays(9), null))
                .andExpect(status().isConflict());
        authed(put("/api/guide/availability/" + existing), guide, range(start.minusDays(1), start.plusDays(5), null))
                .andExpect(status().isOk());

        // Another guide's days don't collide.
        create(tokenWithRole(uniqueEmail(), "GUIDE"), start, start.plusDays(9), null);
    }

    @Test
    void pastRangesAreKeptButHidden() throws Exception {
        String email = uniqueEmail();
        String guide = tokenWithRole(email, "GUIDE");
        UUID guideId = userIdByEmail(email);
        jdbc.update("""
                INSERT INTO guide_availability (id, guide_id, start_date, end_date, created_at)
                VALUES (?, ?, ?, ?, now())""", UUID.randomUUID(), guideId, today().minusDays(10), today().minusDays(3));
        UUID ongoing = create(guide, today().minusDays(2), today().plusDays(2), null);

        authed(get("/api/guide/availability"), guide, null)
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].id").value(ongoing.toString()));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM guide_availability WHERE guide_id = ?", Integer.class,
                guideId)).isEqualTo(2);
    }

    @Test
    void aGuideTouchesOnlyTheirOwnRanges() throws Exception {
        String owner = tokenWithRole(uniqueEmail(), "GUIDE");
        String other = tokenWithRole(uniqueEmail(), "GUIDE");
        LocalDate start = today().plusDays(15);
        UUID id = create(owner, start, start.plusDays(3), null);

        authed(put("/api/guide/availability/" + id), other, range(start, start.plusDays(1), null))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("AVAILABILITY_NOT_FOUND"));
        authed(delete("/api/guide/availability/" + id), other, null).andExpect(status().isNotFound());
        authed(get("/api/guide/availability"), other, null).andExpect(jsonPath("$.items", hasSize(0)));
        authed(get("/api/guide/availability"), owner, null).andExpect(jsonPath("$.items", hasSize(1)));
    }

    @Test
    void notPublicAndNotForTrekkers() throws Exception {
        String email = uniqueEmail();
        String guide = tokenWithRole(email, "GUIDE");
        UUID guideId = userIdByEmail(email);
        create(guide, today().plusDays(5), today().plusDays(8), "Free all week");
        String trekker = emailTrekker(uniqueEmail());

        mockMvc.perform(get("/api/guide/availability")).andExpect(status().isUnauthorized());
        authed(get("/api/guide/availability"), trekker, null).andExpect(status().isForbidden());
        authed(post("/api/guide/availability"), trekker, range(today(), today(), null)).andExpect(status().isForbidden());
        authed(get("/api/admin/guides/availability?from=%s&to=%s".formatted(today(), today().plusDays(30))),
                guide, null).andExpect(status().isForbidden());
        String page = mockMvc.perform(get("/api/public/guides/" + guideId))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(page).doesNotContain("Free all week");
    }

    @Test
    void adminsSeeEveryGuidesRangesInAWindow() throws Exception {
        String admin = adminToken();
        String aEmail = uniqueEmail();
        String a = tokenWithRole(aEmail, "GUIDE");
        String b = tokenWithRole(uniqueEmail(), "GUIDE");
        UUID aId = userIdByEmail(aEmail);
        jdbc.update("UPDATE users SET full_name = 'Shrey Rai' WHERE id = ?", aId);
        authed(get("/api/guide/profile"), a, null).andExpect(status().isOk());
        // Far enough out that other tests' ranges don't land in the window.
        LocalDate from = today().plusYears(3);
        LocalDate to = from.plusDays(9);
        UUID inside = create(a, from.plusDays(2), from.plusDays(4), "Can do Kedarkantha");
        UUID straddling = create(b, from.minusDays(3), from, null);
        create(a, to.plusDays(1), to.plusDays(5), null);
        String disabledEmail = uniqueEmail();
        String disabled = tokenWithRole(disabledEmail, "GUIDE");
        create(disabled, from, to, null);
        jdbc.update("UPDATE users SET status = 'DISABLED' WHERE email = ?", disabledEmail);

        String body = authed(get("/api/admin/guides/availability?from=%s&to=%s".formatted(from, to)), admin, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(2)))
                .andExpect(jsonPath("$.items[0].id").value(straddling.toString()))
                .andExpect(jsonPath("$.items[1].id").value(inside.toString()))
                .andExpect(jsonPath("$.items[1].guide_id").value(aId.toString()))
                .andExpect(jsonPath("$.items[1].guide_name").value("Shrey Rai"))
                .andExpect(jsonPath("$.items[1].slug").isString())
                .andExpect(jsonPath("$.items[1].note").value("Can do Kedarkantha"))
                .andReturn().getResponse().getContentAsString();
        List<String> ids = JsonPath.read(body, "$.items[*].id");
        assertThat(ids).hasSize(2);

        authed(get("/api/admin/guides/availability?from=%s".formatted(from)), admin, null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.fields.to").exists());
        authed(get("/api/admin/guides/availability?from=%s&to=%s".formatted(to, from)), admin, null)
                .andExpect(status().isBadRequest());
        authed(get("/api/admin/guides/availability?from=%s&to=%s".formatted(from, from.plusDays(500))), admin, null)
                .andExpect(status().isBadRequest());
    }

    private UUID create(String token, LocalDate start, LocalDate end, String note) throws Exception {
        String body = authed(post("/api/guide/availability"), token, range(start, end, note))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(JsonPath.read(body, "$.id"));
    }

    private static String range(LocalDate start, LocalDate end, String note) {
        return """
                {"start_date":"%s","end_date":"%s","note":%s}"""
                .formatted(start, end, note == null ? "null" : "\"" + note + "\"");
    }
}
