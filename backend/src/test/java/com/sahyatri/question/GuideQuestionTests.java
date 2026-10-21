package com.sahyatri.question;

import com.jayway.jsonpath.JsonPath;
import com.sahyatri.auth.AuthTestSupport;
import com.sahyatri.question.service.QuestionLinks;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.ResultActions;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Questions to a guide, replies, visitor links, admin close and reply time (docs/TRD.md §7.21.6). */
class GuideQuestionTests extends AuthTestSupport {

    private static final Pattern VISITOR_LINK = Pattern.compile("/questions/([0-9a-f-]{36}\\.\\d+\\.[0-9a-f]{64})");

    @Autowired
    private QuestionLinks links;

    private record Account(UUID id, String email, String token) {
    }

    private Account guide() throws Exception {
        String email = uniqueEmail();
        String token = tokenWithRole(email, "GUIDE");
        return new Account(userIdByEmail(email), email, token);
    }

    private Account trekker() throws Exception {
        String email = uniqueEmail();
        String token = emailTrekker(email);
        return new Account(userIdByEmail(email), email, token);
    }

    /** Every email sent to this address, as [subject, body]. */
    private List<String[]> mailsTo(String email) {
        ArgumentCaptor<String> subject = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        Mockito.verify(mailTransport, Mockito.atLeast(0)).send(eq(email), subject.capture(), body.capture());
        return java.util.stream.IntStream.range(0, subject.getAllValues().size())
                .mapToObj(i -> new String[]{subject.getAllValues().get(i), body.getAllValues().get(i)})
                .toList();
    }

    private String[] lastMailTo(String email) {
        List<String[]> mails = mailsTo(email);
        assertThat(mails).as("mail to " + email).isNotEmpty();
        return mails.getLast();
    }

    private ResultActions ask(Account trekker, String guideKey, String body) throws Exception {
        return authed(post("/api/trekker/guides/" + guideKey + "/questions"), trekker.token(), """
                {"body":"%s"}""".formatted(body));
    }

    private UUID askedThread(Account trekker, Account guide, String body) throws Exception {
        String json = ask(trekker, guide.id().toString(), body).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(JsonPath.read(json, "$.id"));
    }

    private String questionCode(String email) throws Exception {
        postJson("/api/public/questions/email-code", """
                {"email":"%s"}""".formatted(email)).andExpect(status().isAccepted());
        String[] mail = lastMailTo(email.toLowerCase(Locale.ROOT));
        assertThat(mail[0]).contains("question code");
        return mail[0].substring(0, 6);
    }

    private ResultActions visitorAsk(String guideKey, String email, String code, String body) throws Exception {
        return postJson("/api/public/guides/" + guideKey + "/questions", """
                {"name":"Neha Kulkarni","email":"%s","email_code":"%s","body":"%s"}""".formatted(email, code, body));
    }

    private static String wrong(String code) {
        return code.equals("000000") ? "111111" : "000000";
    }

    private static String linkToken(String mailBody) {
        Matcher m = VISITOR_LINK.matcher(mailBody);
        assertThat(m.find()).as("visitor link in: " + mailBody).isTrue();
        return m.group(1);
    }

    private ResultActions guideReply(Account guide, UUID thread, String body) throws Exception {
        return authed(post("/api/guide/questions/" + thread + "/messages"), guide.token(), """
                {"body":"%s"}""".formatted(body));
    }

    @Test
    void aSignedInTrekkerAsksAndTheGuideIsEmailedWithoutTheirContactDetails() throws Exception {
        Account guide = guide();
        Account trekker = trekker();
        String slug = JsonPath.read(authed(get("/api/guide/profile"), guide.token(), null)
                .andReturn().getResponse().getContentAsString(), "$.slug");

        String json = ask(trekker, slug, "Is Kedarkantha in December OK for a first trek? Call me on +91 98765 43210 or "
                + trekker.email())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.guide_slug").value(slug))
                .andExpect(jsonPath("$.asker_name").value("Asha Rao"))
                .andExpect(jsonPath("$.asker_email").value(nullValue()))
                .andExpect(jsonPath("$.can_reply").value(true))
                .andExpect(jsonPath("$.messages", hasSize(1)))
                .andExpect(jsonPath("$.messages[0].author_role").value("TREKKER"))
                .andExpect(jsonPath("$.messages[0].body", containsString("[contact removed]")))
                .andExpect(jsonPath("$.messages[0].body", not(containsString("98765"))))
                .andExpect(jsonPath("$.messages[0].body", not(containsString(trekker.email()))))
                .andReturn().getResponse().getContentAsString();
        UUID thread = UUID.fromString(JsonPath.read(json, "$.id"));

        String[] mail = lastMailTo(guide.email());
        assertThat(mail[0]).isEqualTo("New question from Asha");
        assertThat(mail[1]).contains("Is Kedarkantha in December OK", "/guide/questions/" + thread)
                .doesNotContain(trekker.email(), "98765");

        authed(get("/api/trekker/questions"), trekker.token(), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].id").value(thread.toString()))
                .andExpect(jsonPath("$.items[0].guide_name").value("Asha Rao"));

        ask(trekker, "nobody-" + UUID.randomUUID().toString().substring(0, 8), "Hello?")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("GUIDE_NOT_FOUND"));
        // Only guides take questions.
        ask(trekker, trekker().id().toString(), "Hello?").andExpect(status().isNotFound());
        ask(trekker, guide.id().toString(), " ").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        ask(trekker, guide.id().toString(), "x".repeat(2001)).andExpect(status().isBadRequest());
    }

    @Test
    void theGuideRepliesAndTheTrekkerIsEmailedAndSeesItInTheirAccount() throws Exception {
        Account guide = guide();
        Account trekker = trekker();
        UUID thread = askedThread(trekker, guide, "Which month has the most snow?");

        authed(get("/api/guide/questions"), guide.token(), null)
                .andExpect(jsonPath("$.items[0].id").value(thread.toString()))
                .andExpect(jsonPath("$.items[0].status").value("OPEN"))
                .andExpect(jsonPath("$.items[0].asker_email").value(nullValue()))
                .andExpect(jsonPath("$.items[0].preview").value("Which month has the most snow?"));

        guideReply(guide, thread, "Late January. Write to me at " + guide.email())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ANSWERED"))
                .andExpect(jsonPath("$.first_reply_at").isNotEmpty())
                .andExpect(jsonPath("$.messages[1].author_role").value("GUIDE"))
                .andExpect(jsonPath("$.messages[1].body").value("Late January. Write to me at [contact removed]"));

        String[] mail = lastMailTo(trekker.email());
        assertThat(mail[0]).isEqualTo("Asha replied to your question");
        assertThat(mail[1]).contains("Late January", "/account/questions/" + thread).doesNotContain(guide.email());

        authed(get("/api/trekker/questions/" + thread), trekker.token(), null)
                .andExpect(jsonPath("$.messages", hasSize(2)))
                .andExpect(jsonPath("$.messages[1].author_name").value("Asha Rao"));

        // The trekker writes back: it waits on the guide again, and the guide is told.
        authed(post("/api/trekker/questions/" + thread + "/messages"), trekker.token(), """
                {"body":"Thanks! And gear rental?"}""")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OPEN"));
        assertThat(lastMailTo(guide.email())[0]).isEqualTo("Asha replied to you");

        // Unanswered threads come first in the guide's inbox.
        UUID answered = askedThread(trekker(), guide, "Do you lead in May?");
        guideReply(guide, answered, "Yes.").andExpect(status().isOk());
        authed(get("/api/guide/questions"), guide.token(), null)
                .andExpect(jsonPath("$.items", hasSize(2)))
                .andExpect(jsonPath("$.items[0].id").value(thread.toString()))
                .andExpect(jsonPath("$.items[1].status").value("ANSWERED"))
                .andExpect(jsonPath("$.items[1].last_author_role").value("GUIDE"));
    }

    @Test
    void aVisitorAsksWithAnEmailCode() throws Exception {
        Account guide = guide();
        String email = uniqueEmail();
        String key = guide.id().toString();

        // A code was never sent to this address.
        visitorAsk(key, email, "123456", "Hi")
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.code").value("EMAIL_CODE_EXPIRED"));
        String code = questionCode(email);
        visitorAsk(key, email, wrong(code), "Hi")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("EMAIL_CODE_INVALID"))
                .andExpect(jsonPath("$.details.attempts_left").value(4));
        visitorAsk(key, email, "", "Hi").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM question_threads WHERE guide_id = ?", Integer.class,
                guide.id())).isZero();
        assertThat(mailsTo(guide.email())).isEmpty();

        String id = JsonPath.read(visitorAsk(key, email, code, "Is the Brahmatal trek OK in March?")
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(), "$.id");

        String[] toGuide = lastMailTo(guide.email());
        assertThat(toGuide[0]).isEqualTo("New question from Neha");
        assertThat(toGuide[1]).contains("Brahmatal").doesNotContain(email);
        // The visitor gets a link to their thread straight away.
        String token = linkToken(lastMailTo(email)[1]);
        mockMvc.perform(get("/api/public/questions/" + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.visitor").value(true))
                .andExpect(jsonPath("$.asker_email").value(nullValue()))
                .andExpect(jsonPath("$.messages[0].body").value("Is the Brahmatal trek OK in March?"));
    }

    @Test
    void anExpiredCodeIsRefused() throws Exception {
        Account guide = guide();
        String email = uniqueEmail();
        String code = questionCode(email);
        jdbc.update("UPDATE booking_email_codes SET expires_at = now() - interval '1 minute' WHERE email = ?", email);
        visitorAsk(guide.id().toString(), email, code, "Hi")
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.code").value("EMAIL_CODE_EXPIRED"));
    }

    @Test
    void aVisitorRepliesThroughTheSignedLinkOnly() throws Exception {
        Account guide = guide();
        String email = uniqueEmail();
        UUID thread = UUID.fromString(JsonPath.read(visitorAsk(guide.id().toString(), email, questionCode(email),
                "Can my 12 year old come?").andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(), "$.id"));

        guideReply(guide, thread, "Yes, with a parent.").andExpect(status().isOk());
        String[] mail = lastMailTo(email);
        assertThat(mail[0]).isEqualTo("Asha replied to your question");
        assertThat(mail[1]).contains("Yes, with a parent.").doesNotContain(guide.email());
        String token = linkToken(mail[1]);

        postJson("/api/public/questions/" + token + "/messages", """
                {"body":"Great, thanks."}""")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.messages", hasSize(3)))
                .andExpect(jsonPath("$.messages[2].author_name").value("Neha Kulkarni"));
        assertThat(lastMailTo(guide.email())[0]).isEqualTo("Neha replied to you");

        // Tampered signature, swapped thread, edited expiry, garbage: all refused.
        char last = token.charAt(token.length() - 1);
        String tampered = token.substring(0, token.length() - 1) + (last == '0' ? '1' : '0');
        String[] parts = token.split("\\.");
        String otherThread = UUID.randomUUID() + "." + parts[1] + "." + parts[2];
        String longerExpiry = parts[0] + "." + (Long.parseLong(parts[1]) + 86_400) + "." + parts[2];
        for (String bad : List.of(tampered, otherThread, longerExpiry, "nonsense", parts[0])) {
            mockMvc.perform(get("/api/public/questions/" + bad))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("QUESTION_LINK_INVALID"));
            postJson("/api/public/questions/" + bad + "/messages", """
                    {"body":"sneaky"}""").andExpect(status().isForbidden());
        }

        String expired = links.issue(thread, Instant.now().minusSeconds(1));
        mockMvc.perform(get("/api/public/questions/" + expired))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.code").value("QUESTION_LINK_EXPIRED"));
        postJson("/api/public/questions/" + expired + "/messages", """
                {"body":"late"}""").andExpect(status().isGone());

        // A validly signed link only opens visitor threads; an account's thread stays in the account.
        UUID accountThread = askedThread(trekker(), guide, "Hi");
        mockMvc.perform(get("/api/public/questions/" + links.issue(accountThread)))
                .andExpect(status().isNotFound());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM question_messages WHERE thread_id = ?", Integer.class,
                thread)).isEqualTo(3);
    }

    @Test
    void nobodyElseReadsOrWritesAThread() throws Exception {
        Account guide = guide();
        Account trekker = trekker();
        UUID thread = askedThread(trekker, guide, "Private question");
        Account otherGuide = guide();
        Account otherTrekker = trekker();

        authed(get("/api/guide/questions/" + thread), otherGuide.token(), null)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("QUESTION_NOT_FOUND"));
        guideReply(otherGuide, thread, "Hijack").andExpect(status().isNotFound());
        authed(get("/api/guide/questions"), otherGuide.token(), null).andExpect(jsonPath("$.items", hasSize(0)));
        authed(get("/api/trekker/questions/" + thread), otherTrekker.token(), null).andExpect(status().isNotFound());
        authed(post("/api/trekker/questions/" + thread + "/messages"), otherTrekker.token(), """
                {"body":"Hijack"}""").andExpect(status().isNotFound());
        authed(get("/api/trekker/questions"), otherTrekker.token(), null).andExpect(jsonPath("$.items", hasSize(0)));

        // Wrong role entirely.
        authed(get("/api/guide/questions"), trekker.token(), null).andExpect(status().isForbidden());
        authed(get("/api/admin/questions"), guide.token(), null).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/trekker/questions")).andExpect(status().isUnauthorized());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM question_messages WHERE thread_id = ?", Integer.class,
                thread)).isEqualTo(1);
    }

    @Test
    void anAdminReadsEveryThreadAndClosesOne() throws Exception {
        Account guide = guide();
        Account trekker = trekker();
        UUID thread = askedThread(trekker, guide, "Spam?");
        String adminEmail = uniqueEmail();
        String admin = tokenWithRole(adminEmail, "ADMIN");

        authed(get("/api/admin/questions"), admin, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[?(@.id == '" + thread + "')].asker_email").value(trekker.email()));
        authed(get("/api/admin/questions?status=OPEN"), admin, null)
                .andExpect(jsonPath("$.items[?(@.id == '" + thread + "')]", hasSize(1)));
        authed(get("/api/admin/questions/" + thread), admin, null)
                .andExpect(jsonPath("$.asker_email").value(trekker.email()))
                .andExpect(jsonPath("$.messages", hasSize(1)));

        authed(post("/api/admin/questions/" + thread + "/close"), admin, """
                {"note":"Closed: please keep questions about the trek."}""")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CLOSED"))
                .andExpect(jsonPath("$.can_reply").value(false))
                .andExpect(jsonPath("$.closed_at").isNotEmpty())
                .andExpect(jsonPath("$.messages[1].author_role").value("ADMIN"))
                .andExpect(jsonPath("$.messages[1].author_name").value("The Empty Valley"));
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM audit_events WHERE action = 'QUESTION_THREAD_CLOSED' AND entity_id = ?
                  AND actor_id = ?""", Integer.class, thread, userIdByEmail(adminEmail))).isEqualTo(1);

        guideReply(guide, thread, "Late").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("QUESTION_CLOSED"));
        authed(post("/api/trekker/questions/" + thread + "/messages"), trekker.token(), """
                {"body":"Again"}""").andExpect(status().isConflict());
        authed(post("/api/admin/questions/" + thread + "/close"), admin, null).andExpect(status().isConflict());
        authed(get("/api/admin/questions/" + UUID.randomUUID()), admin, null).andExpect(status().isNotFound());
    }

    @Test
    void askersAreCappedPerDay() throws Exception {
        Account guide = guide();
        Account trekker = trekker();
        for (int i = 0; i < 5; i++) {
            ask(trekker, guide.id().toString(), "Question " + i).andExpect(status().isCreated());
        }
        ask(trekker, guide.id().toString(), "One more")
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("QUESTION_RATE_LIMITED"));
    }

    // ---- Reply time ----

    private void thread(UUID guideId, Duration askedAgo, Duration replyAfter, String status) {
        Instant asked = Instant.now().minus(askedAgo);
        UUID asker = guideId; // any account; the check only needs an asker or a visitor email
        jdbc.update("""
                INSERT INTO question_threads (id, guide_id, asker_id, asker_name, status, created_at, last_message_at,
                    first_reply_at, closed_at)
                VALUES (?, ?, ?, 'Someone', ?, ?, ?, ?, ?)""",
                UUID.randomUUID(), guideId, asker, status, Timestamp.from(asked), Timestamp.from(asked),
                replyAfter == null ? null : Timestamp.from(asked.plus(replyAfter)),
                "CLOSED".equals(status) ? Timestamp.from(asked) : null);
    }

    private ResultActions replyTime(UUID guideId) throws Exception {
        return mockMvc.perform(get("/api/public/guides/" + guideId + "/reply-time")).andExpect(status().isOk());
    }

    @Test
    void replyTimeIsTheMedianFirstReplyAndHiddenBelowFiveThreads() throws Exception {
        UUID guide = guide().id();
        replyTime(guide).andExpect(jsonPath("$.median_minutes").value(nullValue()));

        thread(guide, Duration.ofDays(2), Duration.ofMinutes(30), "ANSWERED");
        thread(guide, Duration.ofDays(3), Duration.ofMinutes(60), "ANSWERED");
        thread(guide, Duration.ofDays(4), Duration.ofMinutes(120), "OPEN");
        thread(guide, Duration.ofDays(5), Duration.ofMinutes(240), "ANSWERED");
        // None of these count: too old, closed without a reply, unanswered but asked under a day ago.
        thread(guide, Duration.ofDays(91), Duration.ofMinutes(1), "ANSWERED");
        thread(guide, Duration.ofDays(6), null, "CLOSED");
        thread(guide, Duration.ofHours(2), null, "OPEN");
        replyTime(guide).andExpect(jsonPath("$.median_minutes").value(nullValue()));

        thread(guide, Duration.ofDays(6), Duration.ofMinutes(480), "ANSWERED");
        replyTime(guide).andExpect(jsonPath("$.median_minutes").value(120));

        // An unanswered question older than a day counts with the time it has waited: 30, 60, 120, 240, 480, ~4320.
        thread(guide, Duration.ofDays(3), null, "OPEN");
        replyTime(guide).andExpect(jsonPath("$.median_minutes").value(180));

        mockMvc.perform(get("/api/public/guides/" + UUID.randomUUID() + "/reply-time"))
                .andExpect(status().isNotFound());
    }
}
