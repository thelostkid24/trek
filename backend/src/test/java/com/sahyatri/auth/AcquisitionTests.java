package com.sahyatri.auth;

import com.jayway.jsonpath.JsonPath;
import com.sahyatri.auth.dto.GoogleIdentity;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Where accounts and bookings come from, heard-from, marketing consent and last seen (§7.15). */
class AcquisitionTests extends AuthTestSupport {

    private static final String FIRST_TOUCH = """
            {"utm_source":"Instagram","utm_medium":"paid","utm_campaign":"kedarkantha_dec","utm_term":"winter trek",
             "utm_content":"reel-1","fbclid":"fb.123","referrer":"https://l.instagram.com/?u=x",
             "landing_path":"/treks/kedarkantha?utm_source=instagram","seen_at":"2026-01-10T05:30:00Z"}""";

    private static String acquisition(String extra) {
        return """
                {"first_touch":%s,"last_touch":{"utm_source":"google","gclid":"g.456","landing_path":"/treks"},
                 "device_type":"MOBILE"%s}""".formatted(FIRST_TOUCH, extra);
    }

    private Map<String, Object> userRow(UUID id) {
        return jdbc.queryForMap("SELECT * FROM users WHERE id = ?", id);
    }

    private UUID userId(MvcResult result) throws Exception {
        return UUID.fromString(JsonPath.read(result.getResponse().getContentAsString(), "$.user.id"));
    }

    private long consentEvents(UUID userId, String action) {
        return jdbc.queryForObject("SELECT count(*) FROM audit_events WHERE entity_id = ? AND action = ?",
                Long.class, userId, action);
    }

    @Test
    void emailSignupKeepsFirstTouchHeardFromAndConsent() throws Exception {
        MvcResult result = postJson("/api/auth/signup", """
                {"full_name":"Asha Rao","email":"%s","password":"trekking1","acquisition":%s}"""
                .formatted(uniqueEmail(), acquisition("""
                        ,"heard_from":"OTHER","heard_from_note":"  Trek meetup in Pune ",
                        "marketing_email":true,"marketing_whatsapp":false""")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.user.marketing_email").value(true))
                .andExpect(jsonPath("$.user.marketing_whatsapp").value(false))
                .andReturn();
        UUID id = userId(result);

        Map<String, Object> row = userRow(id);
        assertThat(row.get("signup_method")).isEqualTo("EMAIL");
        assertThat(row.get("utm_source")).isEqualTo("instagram");
        assertThat(row.get("utm_medium")).isEqualTo("paid");
        assertThat(row.get("utm_campaign")).isEqualTo("kedarkantha_dec");
        assertThat(row.get("utm_term")).isEqualTo("winter trek");
        assertThat(row.get("utm_content")).isEqualTo("reel-1");
        assertThat(row.get("fbclid")).isEqualTo("fb.123");
        assertThat(row.get("gclid")).isNull();
        assertThat(row.get("referrer")).isEqualTo("https://l.instagram.com/?u=x");
        assertThat(row.get("landing_path")).isEqualTo("/treks/kedarkantha?utm_source=instagram");
        assertThat(((Timestamp) row.get("first_seen_at")).toInstant()).isEqualTo(Instant.parse("2026-01-10T05:30:00Z"));
        assertThat(row.get("device_type")).isEqualTo("MOBILE");
        assertThat(row.get("heard_from")).isEqualTo("OTHER");
        assertThat(row.get("heard_from_note")).isEqualTo("Trek meetup in Pune");
        assertThat(row.get("marketing_email_consent_at")).isNotNull();
        assertThat(row.get("marketing_whatsapp_consent_at")).isNull();
        assertThat(row.get("last_seen_at")).isNotNull();

        assertThat(consentEvents(id, "MARKETING_CONSENT_GRANTED")).isEqualTo(1);
        assertThat(jdbc.queryForObject("""
                SELECT data ->> 'via' FROM audit_events WHERE entity_id = ? AND action = 'MARKETING_CONSENT_GRANTED'""",
                String.class, id)).isEqualTo("SIGNUP");
    }

    @Test
    void noAcquisitionStillCreatesTheAccount() throws Exception {
        String email = uniqueEmail();
        emailTrekker(email);
        Map<String, Object> row = userRow(userIdByEmail(email));
        assertThat(row.get("signup_method")).isEqualTo("EMAIL");
        assertThat(row.get("utm_source")).isNull();
        assertThat(row.get("device_type")).isNull();
        assertThat(row.get("marketing_email_consent_at")).isNull();
    }

    @Test
    void trackingValuesAreCleanedNotRejected() throws Exception {
        String longTag = "A".repeat(300);
        String future = Instant.now().plus(3, ChronoUnit.DAYS).toString();
        MvcResult result = postJson("/api/auth/signup", """
                {"full_name":"Asha Rao","email":"%s","password":"trekking1","acquisition":{
                 "last_touch":{"utm_source":"%s","utm_campaign":"   ","referrer":"javascript:alert(1)",
                               "landing_path":"treks","seen_at":"%s"},
                 "device_type":"FRIDGE"}}""".formatted(uniqueEmail(), longTag, future))
                .andExpect(status().isCreated())
                .andReturn();

        Map<String, Object> row = userRow(userId(result));
        assertThat(row.get("utm_source")).isEqualTo("a".repeat(200));
        assertThat(row.get("utm_campaign")).isNull();
        assertThat(row.get("referrer")).isNull();
        assertThat(row.get("landing_path")).isNull();
        assertThat(row.get("device_type")).isNull();
        assertThat(((Timestamp) row.get("first_seen_at")).toInstant()).isBefore(Instant.now().plusSeconds(1));
    }

    @Test
    void userTypedFieldsAreValidated() throws Exception {
        postJson("/api/auth/signup", """
                {"full_name":"Asha Rao","email":"%s","password":"trekking1",
                 "acquisition":{"heard_from":"OTHER","heard_from_note":"%s"}}""".formatted(uniqueEmail(), "x".repeat(201)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.details.fields['acquisition.heard_from_note']").exists());

        postJson("/api/auth/signup", """
                {"full_name":"Asha Rao","email":"%s","password":"trekking1",
                 "acquisition":{"heard_from":"CARRIER_PIGEON"}}""".formatted(uniqueEmail()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void phoneSignInKeepsTheFirstTouchOfTheNewAccountOnly() throws Exception {
        String phone = uniquePhone();
        postJson("/api/auth/otp/request", """
                {"phone":"%s"}""".formatted(phone)).andExpect(status().isAccepted());
        MvcResult first = postJson("/api/auth/otp/verify", """
                {"phone":"%s","code":"%s","acquisition":%s}"""
                .formatted(phone, lastOtp(phone), acquisition(",\"heard_from\":\"YOUTUBE\"")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.is_new_user").value(true))
                .andReturn();
        UUID id = userId(first);

        // Past the resend cooldown.
        jdbc.update("UPDATE otp_challenges SET created_at = created_at - interval '1 minute' WHERE phone = ?", phone);
        postJson("/api/auth/otp/request", """
                {"phone":"%s"}""".formatted(phone)).andExpect(status().isAccepted());
        postJson("/api/auth/otp/verify", """
                {"phone":"%s","code":"%s","acquisition":{"first_touch":{"utm_source":"newsletter"},
                 "device_type":"DESKTOP","heard_from":"INSTAGRAM","marketing_email":true}}"""
                .formatted(phone, lastOtp(phone)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.is_new_user").value(false));

        Map<String, Object> row = userRow(id);
        assertThat(row.get("signup_method")).isEqualTo("PHONE");
        assertThat(row.get("utm_source")).isEqualTo("instagram");
        assertThat(row.get("device_type")).isEqualTo("MOBILE");
        assertThat(row.get("heard_from")).isEqualTo("YOUTUBE");
        assertThat(row.get("marketing_email_consent_at")).isNull();
    }

    @Test
    void googleSignupRecordsItsMethodAndConsent() throws Exception {
        when(googleTokenVerifier.verify("acq-token"))
                .thenReturn(new GoogleIdentity("g-" + UUID.randomUUID(), uniqueEmail(), true, "Meera S"));
        MvcResult result = postJson("/api/auth/google", """
                {"id_token":"acq-token","acquisition":%s}""".formatted(acquisition(",\"marketing_whatsapp\":true")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.marketing_whatsapp").value(true))
                .andReturn();
        UUID id = userId(result);

        assertThat(userRow(id).get("signup_method")).isEqualTo("GOOGLE");
        assertThat(userRow(id).get("utm_campaign")).isEqualTo("kedarkantha_dec");
        assertThat(consentEvents(id, "MARKETING_CONSENT_GRANTED")).isEqualTo(1);
    }

    @Test
    void bookingsKeepTheirLastTouch() throws Exception {
        UUID departure = publishedDeparture();
        String token = bookingTrekker();
        MvcResult result = authed(post("/api/trekker/bookings"), token, """
                {"departure_id":"%s","seats":1,"acquisition":%s}""".formatted(departure, acquisition("")))
                .andExpect(status().isCreated())
                .andReturn();
        UUID booking = UUID.fromString(JsonPath.read(result.getResponse().getContentAsString(), "$.id"));

        Map<String, Object> row = jdbc.queryForMap("SELECT * FROM bookings WHERE id = ?", booking);
        assertThat(row.get("utm_source")).isEqualTo("google");
        assertThat(row.get("gclid")).isEqualTo("g.456");
        assertThat(row.get("landing_path")).isEqualTo("/treks");
        assertThat(row.get("utm_campaign")).isNull();
        assertThat(row.get("device_type")).isEqualTo("MOBILE");
        assertThat(row.get("touch_seen_at")).isNotNull();
    }

    @Test
    void guestCheckoutRecordsTheAccountAndTheBooking() throws Exception {
        UUID departure = publishedDeparture();
        MvcResult result = postJson("/api/public/bookings", """
                {"departure_id":"%s","seats":1,"full_name":"Neha Kulkarni","phone":"%s","email":"%s",
                 "acquisition":%s}""".formatted(departure, uniquePhone(), uniqueEmail(),
                        acquisition(",\"heard_from\":\"FRIEND_FAMILY\",\"marketing_whatsapp\":true")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.auth.user.marketing_whatsapp").value(true))
                .andReturn();
        String body = result.getResponse().getContentAsString();
        UUID guest = UUID.fromString(JsonPath.read(body, "$.auth.user.id"));
        UUID booking = UUID.fromString(JsonPath.read(body, "$.booking.id"));

        Map<String, Object> user = userRow(guest);
        assertThat(user.get("signup_method")).isEqualTo("GUEST_CHECKOUT");
        assertThat(user.get("utm_source")).isEqualTo("instagram");
        assertThat(user.get("heard_from")).isEqualTo("FRIEND_FAMILY");
        assertThat(consentEvents(guest, "MARKETING_CONSENT_GRANTED")).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT utm_source FROM bookings WHERE id = ?", String.class, booking))
                .isEqualTo("google");
    }

    @Test
    void consentCanBeChangedFromTheAccountAndEveryChangeIsAudited() throws Exception {
        String email = uniqueEmail();
        String token = emailTrekker(email);
        UUID id = userIdByEmail(email);

        authed(patch("/api/account/marketing-consent"), token, """
                {"email":true,"whatsapp":true}""")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.marketing_email").value(true))
                .andExpect(jsonPath("$.marketing_whatsapp").value(true));
        // Same value again: nothing changes, nothing audited.
        authed(patch("/api/account/marketing-consent"), token, """
                {"email":true}""")
                .andExpect(status().isOk());
        authed(patch("/api/account/marketing-consent"), token, """
                {"whatsapp":false}""")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.marketing_email").value(true))
                .andExpect(jsonPath("$.marketing_whatsapp").value(false));

        assertThat(consentEvents(id, "MARKETING_CONSENT_GRANTED")).isEqualTo(2);
        assertThat(consentEvents(id, "MARKETING_CONSENT_WITHDRAWN")).isEqualTo(1);
        assertThat(userRow(id).get("marketing_whatsapp_consent_at")).isNull();
        assertThat(jdbc.queryForObject("""
                SELECT data ->> 'via' FROM audit_events WHERE entity_id = ? AND action = 'MARKETING_CONSENT_WITHDRAWN'""",
                String.class, id)).isEqualTo("ACCOUNT");
    }

    @Test
    void lastSeenMovesOnRefreshAtMostHourly() throws Exception {
        MvcResult signedUp = signup(uniqueEmail(), "trekking1").andExpect(status().isCreated()).andReturn();
        UUID id = userId(signedUp);
        String cookie = refreshToken(signedUp);

        Instant tenMinutesAgo = Instant.now().minus(10, ChronoUnit.MINUTES).truncatedTo(ChronoUnit.MILLIS);
        jdbc.update("UPDATE users SET last_seen_at = ? WHERE id = ?", Timestamp.from(tenMinutesAgo), id);
        MvcResult refreshed = mockMvc.perform(post("/api/auth/refresh").cookie(refreshCookie(cookie)))
                .andExpect(status().isOk()).andReturn();
        assertThat(lastSeen(id)).isEqualTo(tenMinutesAgo);

        Instant twoHoursAgo = Instant.now().minus(2, ChronoUnit.HOURS);
        jdbc.update("UPDATE users SET last_seen_at = ? WHERE id = ?", Timestamp.from(twoHoursAgo), id);
        mockMvc.perform(post("/api/auth/refresh").cookie(refreshCookie(refreshToken(refreshed))))
                .andExpect(status().isOk());
        assertThat(lastSeen(id)).isAfter(Instant.now().minus(1, ChronoUnit.MINUTES));
    }

    private Instant lastSeen(UUID id) {
        return jdbc.queryForObject("SELECT last_seen_at FROM users WHERE id = ?", Timestamp.class, id).toInstant();
    }
}
