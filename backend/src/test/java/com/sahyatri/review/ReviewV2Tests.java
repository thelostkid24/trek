package com.sahyatri.review;

import com.jayway.jsonpath.JsonPath;
import com.sahyatri.auth.AuthTestSupport;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Reviews v2 (§7.21.5): sub-ratings, tags, own trekkers, replies, reports, moderation. */
class ReviewV2Tests extends AuthTestSupport {

    private static final String REVIEW = "/api/trekker/bookings/%s/review";

    /** A guide with a token, a slug, and a completed departure with {@code trekkers} confirmed bookings. */
    private record Setup(String guideToken, UUID guideId, String slug, UUID departure, UUID trackId,
                         List<String> trekkers, List<UUID> bookings) {
    }

    private Setup completedTrek(int trekkers) throws Exception {
        String email = uniqueEmail();
        String guideToken = tokenWithRole(email, "GUIDE");
        UUID guideId = userIdByEmail(email);
        String slug = JsonPath.read(authed(get("/api/guide/profile"), guideToken, null)
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(), "$.slug");
        String admin = adminToken();
        UUID track = createTrack(admin, 2);
        UUID departure = createDraft(admin, track, guideId, today().plusDays(30), 219_900, 10);
        authed(post("/api/admin/departures/" + departure + "/publish"), admin, null).andExpect(status().isOk());
        List<String> tokens = new ArrayList<>();
        List<UUID> bookings = new ArrayList<>();
        for (int i = 0; i < trekkers; i++) {
            String t = bookingTrekker();
            tokens.add(t);
            bookings.add(confirmedBooking(t, departure, 1));
        }
        jdbc.update("UPDATE departures SET status = 'COMPLETED' WHERE id = ?", departure);
        return new Setup(guideToken, guideId, slug, departure, track, tokens, bookings);
    }

    private String reviewEmailCode(String slug, String email) throws Exception {
        postJson("/api/public/guides/" + slug + "/reviews/code", """
                {"channel":"EMAIL","email":"%s"}""".formatted(email)).andExpect(status().isAccepted());
        ArgumentCaptor<String> subject = ArgumentCaptor.forClass(String.class);
        Mockito.verify(mailTransport, Mockito.atLeastOnce()).send(eq(email), subject.capture(), anyString());
        return subject.getValue().substring(0, 6);
    }

    private UUID postOwnReview(String slug, String email, int rating) throws Exception {
        String code = reviewEmailCode(slug, email);
        String body = postJson("/api/public/guides/" + slug + "/reviews", """
                {"rating":%d,"name":"Kiran Shah","email":"%s","code":"%s","trek_name":"Hampta Pass"}"""
                .formatted(rating, email, code)).andExpect(status().isOk()).andReturn().getResponse()
                .getContentAsString();
        return UUID.fromString(JsonPath.read(body, "$.review.id"));
    }

    @Test
    void subRatingBarsNeedFiveAnswersAndTagsNeedTwoMentions() throws Exception {
        Setup s = completedTrek(5);
        // Four rate safety; tags: FELT_SAFE twice, GREAT_FOOD once.
        for (int i = 0; i < 4; i++) {
            String tags = i < 2 ? "[\"FELT_SAFE\",\"GOOD_PACE\"]" : i == 2 ? "[\"GREAT_FOOD\"]" : "[]";
            authed(put(REVIEW.formatted(s.bookings().get(i))), s.trekkers().get(i), """
                    {"rating":%d,"safety_rating":5,"route_rating":4,"tags":%s}""".formatted(i % 2 == 0 ? 5 : 4, tags))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.safety_rating").value(5));
        }
        mockMvc.perform(get("/api/public/guides/" + s.slug() + "/reviews-summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.average").value(4.5))
                .andExpect(jsonPath("$.count").value(4))
                .andExpect(jsonPath("$.categories", hasSize(1)))
                .andExpect(jsonPath("$.categories[0].key").value("OVERALL"))
                .andExpect(jsonPath("$.mentioned_often[0].tag").value("FELT_SAFE"))
                .andExpect(jsonPath("$.mentioned_often[0].count").value(2))
                .andExpect(jsonPath("$.mentioned_often[*].tag", not(hasItem("GREAT_FOOD"))));

        // The fifth answer brings the safety bar; route stays hidden (four answers); unanswered care never shows.
        authed(put(REVIEW.formatted(s.bookings().get(4))), s.trekkers().get(4), """
                {"rating":4,"safety_rating":3,"communication_rating":5,"tags":["KNEW_THE_ROUTE"]}""")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tags[0]").value("KNEW_THE_ROUTE"));
        mockMvc.perform(get("/api/public/guides/" + s.slug() + "/reviews-summary"))
                .andExpect(jsonPath("$.categories", hasSize(2)))
                .andExpect(jsonPath("$.categories[0].key").value("OVERALL"))
                .andExpect(jsonPath("$.categories[1].key").value("SAFETY"))
                .andExpect(jsonPath("$.categories[1].average").value(4.6))
                .andExpect(jsonPath("$.categories[1].answers").value(5));

        // Unknown tags, too many tags and out-of-range sub-ratings fail validation.
        authed(put(REVIEW.formatted(s.bookings().get(0))), s.trekkers().get(0), """
                {"rating":5,"tags":["NICE_HAT"]}""").andExpect(status().isBadRequest());
        authed(put(REVIEW.formatted(s.bookings().get(0))), s.trekkers().get(0), """
                {"rating":5,"care_rating":6}""").andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/public/guides/" + s.guideId() + "/reviews"))
                .andExpect(jsonPath("$.total").value(5))
                .andExpect(jsonPath("$.items[0].source").value("BOOKING"))
                .andExpect(jsonPath("$.items[0].trek_name").value("Rajmachi Fort"));
    }

    @Test
    void ownTrekkersReviewWithAnEmailCodeOncePerIdentity() throws Exception {
        Setup s = completedTrek(0);
        String email = uniqueEmail();
        String url = "/api/public/guides/" + s.slug() + "/reviews";
        String code = reviewEmailCode(s.slug(), email);
        String wrong = code.equals("000000") ? "111111" : "000000";

        postJson(url, """
                {"rating":5,"name":"Kiran Shah","email":"%s","code":"%s"}""".formatted(email, wrong))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("EMAIL_CODE_INVALID"));
        // Both or neither of email and phone.
        postJson(url, """
                {"rating":5,"name":"Kiran","email":"%s","phone":"+919812345670","code":"%s"}""".formatted(email, code))
                .andExpect(status().isBadRequest());
        postJson(url, """
                {"rating":5,"name":"Kiran","code":"%s"}""".formatted(code))
                .andExpect(status().isBadRequest());
        postJson(url, """
                {"rating":5,"name":"Kiran Shah","email":"%s","code":"%s","body":"Steady and calm.",
                 "tags":["CALM_IN_BAD_WEATHER"],"care_rating":5,"trek_name":"Hampta Pass"}""".formatted(email, code))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.created").value(true))
                .andExpect(jsonPath("$.review.source").value("OWN_TREKKER"))
                .andExpect(jsonPath("$.review.author_name").value("Kiran"))
                .andExpect(jsonPath("$.review.trek_name").value("Hampta Pass"))
                .andExpect(jsonPath("$.review.trek_start_date").value(nullValue()));
        // The same email again rewrites the one review (a correct code stays good until it expires).
        postJson(url, """
                {"rating":3,"name":"Kiran","email":"%s","code":"%s"}""".formatted(email.toUpperCase(), code))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.created").value(false))
                .andExpect(jsonPath("$.review.rating").value(3));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM reviews WHERE guide_id = ?", Long.class, s.guideId()))
                .isEqualTo(1);

        // An expired code.
        jdbc.update("UPDATE booking_email_codes SET expires_at = now() - interval '1 minute' WHERE email = ?", email);
        postJson(url, """
                {"rating":4,"name":"Kiran","email":"%s","code":"%s"}""".formatted(email, code))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.code").value("EMAIL_CODE_EXPIRED"));

        // By phone instead.
        String phone = uniquePhone();
        postJson(url + "/code", """
                {"channel":"PHONE","phone":"%s"}""".formatted(phone)).andExpect(status().isAccepted());
        postJson(url, """
                {"rating":5,"name":"Meera","phone":"%s","code":"%s"}""".formatted(phone, lastOtp(phone)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.created").value(true));
        // An OTP is spent once.
        postJson(url, """
                {"rating":5,"name":"Meera","phone":"%s","code":"%s"}""".formatted(phone, lastOtp(phone)))
                .andExpect(status().isGone());

        postJson(url + "/code", "{\"channel\":\"EMAIL\"}").andExpect(status().isBadRequest());
        postJson("/api/public/guides/no-such-guide/reviews/code", """
                {"channel":"EMAIL","email":"%s"}""".formatted(email)).andExpect(status().isNotFound());
    }

    @Test
    void ownTrekkerReviewsNeverCountInAnyHeadlineRating() throws Exception {
        Setup s = completedTrek(1);
        authed(put(REVIEW.formatted(s.bookings().getFirst())), s.trekkers().getFirst(), """
                {"rating":4,"safety_rating":4,"tags":["FELT_SAFE"]}""").andExpect(status().isOk());
        for (int i = 0; i < 5; i++) {
            String email = uniqueEmail();
            String code = reviewEmailCode(s.slug(), email);
            postJson("/api/public/guides/" + s.slug() + "/reviews", """
                    {"rating":1,"safety_rating":1,"tags":["FELT_SAFE"],"name":"X","email":"%s","code":"%s"}"""
                    .formatted(email, code)).andExpect(status().isOk());
        }
        // Another published date by this guide on the same trek, so the trek and departure pages show them.
        String admin = adminToken();
        UUID upcoming = createDraft(admin, s.trackId(), s.guideId(), today().plusDays(40), 219_900, 6);
        authed(post("/api/admin/departures/" + upcoming + "/publish"), admin, null).andExpect(status().isOk());
        String trackSlug = jdbc.queryForObject("SELECT slug FROM tracks WHERE id = ?", String.class, s.trackId());

        mockMvc.perform(get("/api/public/guides/" + s.slug()))
                .andExpect(jsonPath("$.rating").value(4.0))
                .andExpect(jsonPath("$.review_count").value(1));
        mockMvc.perform(get("/api/public/guides"))
                .andExpect(jsonPath("$.items[?(@.id == '%s')].rating".formatted(s.guideId())).value(4.0))
                .andExpect(jsonPath("$.items[?(@.id == '%s')].review_count".formatted(s.guideId())).value(1));
        mockMvc.perform(get("/api/public/tracks/" + trackSlug))
                .andExpect(jsonPath("$.departures[0].guide.rating").value(4.0))
                .andExpect(jsonPath("$.departures[0].guide.review_count").value(1));
        mockMvc.perform(get("/api/public/departures/" + upcoming))
                .andExpect(jsonPath("$.guide.rating").value(4.0))
                .andExpect(jsonPath("$.guide.review_count").value(1));
        mockMvc.perform(get("/api/public/guides/" + s.slug() + "/reviews-summary"))
                .andExpect(jsonPath("$.average").value(4.0))
                .andExpect(jsonPath("$.count").value(1))
                .andExpect(jsonPath("$.counts.all").value(6))
                .andExpect(jsonPath("$.counts.booking").value(1))
                .andExpect(jsonPath("$.counts.own_trekker").value(5))
                .andExpect(jsonPath("$.categories", hasSize(1)))
                .andExpect(jsonPath("$.categories[0].key").value("OVERALL"))
                .andExpect(jsonPath("$.mentioned_often", hasSize(0)));
        mockMvc.perform(get("/api/public/guides/" + s.slug() + "/reviews?source=OWN_TREKKER&size=2"))
                .andExpect(jsonPath("$.total").value(5))
                .andExpect(jsonPath("$.items", hasSize(2)))
                .andExpect(jsonPath("$.items[0].source").value("OWN_TREKKER"));
        authed(get("/api/guide/reviews"), s.guideToken(), null)
                .andExpect(jsonPath("$.average").value(4.0))
                .andExpect(jsonPath("$.count").value(1))
                .andExpect(jsonPath("$.own_trekker_count").value(5));
        authed(get("/api/admin/insights?days=30"), admin, null)
                .andExpect(jsonPath("$.guides[?(@.guide_id == '%s')].avg_rating".formatted(s.guideId())).value(4.0))
                .andExpect(jsonPath("$.guides[?(@.guide_id == '%s')].reviews".formatted(s.guideId())).value(1));
    }

    @Test
    void guidesReplyAndReportAndAdminsHideWithAnAuditedReason() throws Exception {
        Setup s = completedTrek(1);
        UUID reviewId = UUID.fromString(JsonPath.read(authed(put(REVIEW.formatted(s.bookings().getFirst())),
                s.trekkers().getFirst(), "{\"rating\":2,\"body\":\"Late every morning.\"}")
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(), "$.id"));
        String reply = "/api/guide/reviews/" + reviewId + "/reply";

        // Only this review's guide may reply; trekkers can't reach the guide area at all.
        String otherGuide = tokenWithRole(uniqueEmail(), "GUIDE");
        authed(put(reply), otherGuide, "{\"body\":\"Not mine\"}").andExpect(status().isNotFound());
        authed(put(reply), s.trekkers().getFirst(), "{\"body\":\"Hi\"}").andExpect(status().isForbidden());
        authed(put(reply), s.guideToken(), "{\"body\":\"  \"}").andExpect(status().isBadRequest());
        authed(put(reply), s.guideToken(), "{\"body\":\"Sorry — the road was shut.\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.review.reply.body").value("Sorry — the road was shut."));
        authed(put(reply), s.guideToken(), "{\"body\":\"Sorry, the road was shut both days.\"}")
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/public/guides/" + s.slug() + "/reviews"))
                .andExpect(jsonPath("$.items[0].reply.body").value("Sorry, the road was shut both days."));
        authed(get("/api/guide/reviews"), s.guideToken(), null)
                .andExpect(jsonPath("$.replied_this_week").value(1))
                .andExpect(jsonPath("$.departures[0].departure_id").value(s.departure().toString()))
                .andExpect(jsonPath("$.items[0].reported").value(false));

        // Report; guides can't delete reviews.
        authed(post("/api/guide/reviews/" + reviewId + "/report"), otherGuide, "{\"reason\":\"x\"}")
                .andExpect(status().isNotFound());
        authed(post("/api/guide/reviews/" + reviewId + "/report"), s.guideToken(), "{\"reason\":\"Wasn't on this trek\"}")
                .andExpect(status().isNoContent());
        authed(post("/api/guide/reviews/" + reviewId + "/report"), s.guideToken(), "{\"reason\":\"Not on this trek\"}")
                .andExpect(status().isNoContent());
        authed(delete("/api/guide/reviews/" + reviewId), s.guideToken(), null).andExpect(status().is4xxClientError());
        authed(get("/api/guide/reviews"), s.guideToken(), null).andExpect(jsonPath("$.items[0].reported").value(true));

        String admin = adminToken();
        authed(get("/api/admin/reviews?view=REPORTED"), admin, null)
                .andExpect(jsonPath("$.items[?(@.review.id == '%s')].reports[0].reason".formatted(reviewId))
                        .value("Not on this trek"));
        authed(get("/api/admin/reviews"), s.guideToken(), null).andExpect(status().isForbidden());

        // Hide: gone from the page and the rating, reports closed, audited.
        authed(put("/api/admin/reviews/" + reviewId + "/hidden"), admin, "{\"reason\":\"Abusive language\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hidden_reason").value("Abusive language"));
        mockMvc.perform(get("/api/public/guides/" + s.slug() + "/reviews")).andExpect(jsonPath("$.total").value(0));
        mockMvc.perform(get("/api/public/guides/" + s.slug())).andExpect(jsonPath("$.review_count").value(0));
        authed(get("/api/admin/reviews?view=REPORTED"), admin, null)
                .andExpect(jsonPath("$.items[?(@.review.id == '%s')]".formatted(reviewId), hasSize(0)));
        authed(get("/api/admin/reviews?view=HIDDEN"), admin, null)
                .andExpect(jsonPath("$.items[?(@.review.id == '%s')]".formatted(reviewId), hasSize(1)));
        authed(get("/api/guide/reviews"), s.guideToken(), null)
                .andExpect(jsonPath("$.items[0].hidden_reason").value("Abusive language"))
                .andExpect(jsonPath("$.items[0].reported").value(false));
        assertThat(audit(reviewId, "REVIEW_HIDDEN")).isEqualTo(1);

        authed(delete("/api/admin/reviews/" + reviewId + "/hidden"), admin, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hidden_at").value(nullValue()));
        mockMvc.perform(get("/api/public/guides/" + s.slug() + "/reviews")).andExpect(jsonPath("$.total").value(1));
        assertThat(audit(reviewId, "REVIEW_UNHIDDEN")).isEqualTo(1);

        // Hiding the reply keeps the review; the guide still sees their reply and why.
        authed(put("/api/admin/reviews/" + reviewId + "/reply/hidden"), admin, "{\"reason\":\"Shares a phone number\"}")
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/public/guides/" + s.slug() + "/reviews"))
                .andExpect(jsonPath("$.items[0].reply").value(nullValue()));
        authed(get("/api/guide/reviews"), s.guideToken(), null)
                .andExpect(jsonPath("$.items[0].review.reply.body").exists())
                .andExpect(jsonPath("$.items[0].reply_hidden_reason").value("Shares a phone number"));
        authed(delete("/api/admin/reviews/" + reviewId + "/reply/hidden"), admin, null).andExpect(status().isOk());
        assertThat(audit(reviewId, "REVIEW_REPLY_HIDDEN")).isEqualTo(1);
        assertThat(audit(reviewId, "REVIEW_REPLY_UNHIDDEN")).isEqualTo(1);
        mockMvc.perform(get("/api/public/guides/" + s.slug() + "/reviews"))
                .andExpect(jsonPath("$.items[0].reply.body").exists());

        // The guide can take their reply down.
        authed(delete(reply), s.guideToken(), null).andExpect(status().isNoContent());
        mockMvc.perform(get("/api/public/guides/" + s.slug() + "/reviews"))
                .andExpect(jsonPath("$.items[0].reply").value(nullValue()));
        authed(put("/api/admin/reviews/" + reviewId + "/reply/hidden"), admin, "{\"reason\":\"x\"}")
                .andExpect(status().isNotFound());
    }

    @Test
    void signedInBookersAreSentToTheirBookingReview() throws Exception {
        Setup s = completedTrek(1);
        authed(get("/api/trekker/guides/" + s.slug() + "/review-booking"), s.trekkers().getFirst(), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.booking_id").value(s.bookings().getFirst().toString()))
                .andExpect(jsonPath("$.reviewed").value(false));
        authed(put(REVIEW.formatted(s.bookings().getFirst())), s.trekkers().getFirst(), "{\"rating\":5}")
                .andExpect(status().isOk());
        authed(get("/api/trekker/guides/" + s.guideId() + "/review-booking"), s.trekkers().getFirst(), null)
                .andExpect(jsonPath("$.reviewed").value(true));
        authed(get("/api/trekker/guides/" + s.slug() + "/review-booking"), bookingTrekker(), null)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NO_REVIEWABLE_BOOKING"));
    }

    private long audit(UUID reviewId, String action) {
        return jdbc.queryForObject("SELECT count(*) FROM audit_events WHERE entity_id = ? AND action = ?",
                Long.class, reviewId, action);
    }
}
