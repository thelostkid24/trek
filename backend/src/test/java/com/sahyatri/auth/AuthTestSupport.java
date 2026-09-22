package com.sahyatri.auth;

import com.jayway.jsonpath.JsonPath;
import com.sahyatri.TestcontainersConfiguration;
import com.sahyatri.account.mail.EmailSender;
import com.sahyatri.common.mail.MailTransport;
import com.sahyatri.auth.service.GoogleTokenVerifier;
import com.sahyatri.auth.sms.SmsSender;
import com.sahyatri.common.security.RefreshCookie;
import com.sahyatri.common.util.HashingUtils;
import com.sahyatri.payment.FakePaymentGateway;
import jakarta.servlet.http.Cookie;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.AbstractMockHttpServletRequestBuilder;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static org.mockito.ArgumentMatchers.eq;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Shared context for API tests across features (one Spring context + one Postgres container for all of them).
 * Keep every mock and dynamic property here so subclasses don't fork the context cache.
 */
@Import({TestcontainersConfiguration.class, FakePaymentGateway.Config.class})
@SpringBootTest(properties = "app.rate-limit.enabled=false") // RateLimitFilterTests covers limits
@AutoConfigureMockMvc
public abstract class AuthTestSupport {

    protected static final Path UPLOAD_DIR = createUploadDir();

    /** Listed in app.admin.emails for every test. */
    protected static final String BOOTSTRAP_ADMIN_EMAIL = "bootstrap-admin@example.com";

    protected static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected JdbcTemplate jdbc;

    @MockitoBean
    protected SmsSender smsSender;

    @MockitoBean
    protected EmailSender emailSender;

    @MockitoBean
    protected MailTransport mailTransport;

    @MockitoBean
    protected GoogleTokenVerifier googleTokenVerifier;

    @Autowired
    protected FakePaymentGateway gateway;

    protected static final String WEBHOOK_SECRET = "test-webhook-secret";

    @DynamicPropertySource
    static void storageProperties(DynamicPropertyRegistry registry) {
        registry.add("app.storage.local-dir", UPLOAD_DIR::toString);
        registry.add("app.admin.emails", () -> BOOTSTRAP_ADMIN_EMAIL);
        registry.add("app.payments.webhook-secret", () -> WEBHOOK_SECRET);
        // Tests run the reconciler themselves.
        registry.add("app.bookings.reconcile-initial-delay", () -> "PT24H");
    }

    private static Path createUploadDir() {
        try {
            return Files.createTempDirectory("sahyatri-uploads-");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    protected ResultActions postJson(String path, String json) throws Exception {
        return mockMvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    protected ResultActions signup(String email, String password) throws Exception {
        return postJson("/api/auth/signup", """
                {"full_name":"Asha Rao","email":"%s","password":"%s"}""".formatted(email, password));
    }

    protected static String uniqueEmail() {
        return "user-" + UUID.randomUUID() + "@example.com";
    }

    protected static String uniquePhone() {
        return "+919" + String.format("%09d", ThreadLocalRandom.current().nextInt(1_000_000_000));
    }

    protected static String accessToken(MvcResult result) throws Exception {
        return JsonPath.read(result.getResponse().getContentAsString(), "$.access_token");
    }

    /** Refresh-token value from the Set-Cookie header. */
    protected static String refreshToken(MvcResult result) {
        String header = result.getResponse().getHeader(HttpHeaders.SET_COOKIE);
        if (header == null || !header.startsWith(RefreshCookie.NAME + "=")) {
            throw new AssertionError("No refresh cookie in response: " + header);
        }
        return header.substring(RefreshCookie.NAME.length() + 1, header.indexOf(';'));
    }

    protected static Cookie refreshCookie(String value) {
        return new Cookie(RefreshCookie.NAME, value);
    }

    /** Access token of a fresh email + password trekker (password "trekking1"). */
    protected String emailTrekker(String email) throws Exception {
        return accessToken(signup(email, "trekking1").andExpect(status().isCreated()).andReturn());
    }

    /** Signs up a phone-only trekker through the OTP flow and returns the whole response. */
    protected MvcResult phoneTrekker(String phone) throws Exception {
        postJson("/api/auth/otp/request", """
                {"phone":"%s"}""".formatted(phone)).andExpect(status().isAccepted());
        return postJson("/api/auth/otp/verify", """
                {"phone":"%s","code":"%s"}""".formatted(phone, lastOtp(phone)))
                .andExpect(status().isOk())
                .andReturn();
    }

    /** The most recent code "sent" to this phone. */
    protected String lastOtp(String phone) {
        ArgumentCaptor<String> code = ArgumentCaptor.forClass(String.class);
        Mockito.verify(smsSender, Mockito.atLeastOnce()).sendOtp(eq(phone), code.capture());
        return code.getValue();
    }

    /** Performs the request with a Bearer token and, when given, a JSON body. */
    protected ResultActions authed(AbstractMockHttpServletRequestBuilder<?> request, String token, String json)
            throws Exception {
        request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        if (json != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(json);
        }
        return mockMvc.perform(request);
    }

    protected static LocalDate today() {
        return LocalDate.now(IST);
    }

    protected ResultActions login(String email, String password) throws Exception {
        return postJson("/api/auth/login", """
                {"email":"%s","password":"%s"}""".formatted(email, password));
    }

    /** Signs up an account, sets its role directly in the database, and signs in again for a token with it. */
    protected String tokenWithRole(String email, String role) throws Exception {
        signup(email, "trekking1").andExpect(status().isCreated());
        jdbc.update("UPDATE users SET role = ? WHERE email = ?", role, email);
        return accessToken(login(email, "trekking1").andExpect(status().isOk()).andReturn());
    }

    protected String adminToken() throws Exception {
        return tokenWithRole(uniqueEmail(), "ADMIN");
    }

    /** Creates a guide account and returns its user id. */
    protected UUID guideUser() throws Exception {
        String email = uniqueEmail();
        tokenWithRole(email, "GUIDE");
        return jdbc.queryForObject("SELECT id FROM users WHERE email = ?", UUID.class, email);
    }

    protected static String uniqueSlug() {
        return "track-" + UUID.randomUUID().toString().substring(0, 8);
    }

    /** Creates a track through the admin API and returns its id. */
    protected UUID createTrack(String adminToken, int durationDays) throws Exception {
        MvcResult result = authed(post("/api/admin/tracks"), adminToken, """
                {"slug":"%s","name":"Rajmachi Fort","region":"Lonavala","difficulty":"EASY","duration_days":%d,
                 "max_altitude_m":822,"summary":"Twin forts above Lonavala","description":"A gentle climb.",
                 "meeting_point":"Lonavala station"}""".formatted(uniqueSlug(), durationDays))
                .andExpect(status().isCreated())
                .andReturn();
        return UUID.fromString(JsonPath.read(result.getResponse().getContentAsString(), "$.id"));
    }

    /** Creates a draft departure and returns its id. */
    protected UUID createDraft(String adminToken, UUID trackId, UUID guideId, LocalDate start, long pricePaise,
                               int maxGroupSize) throws Exception {
        MvcResult result = authed(post("/api/admin/departures"), adminToken, """
                {"track_id":"%s","guide_id":"%s","start_date":"%s","price_paise":%d,"max_group_size":%d}"""
                .formatted(trackId, guideId, start, pricePaise, maxGroupSize))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andReturn();
        return UUID.fromString(JsonPath.read(result.getResponse().getContentAsString(), "$.id"));
    }

    /** A published 2-day departure, 30 days out, 6 seats at ₹2,199. */
    protected UUID publishedDeparture() throws Exception {
        return publishedDeparture(today().plusDays(30), 219_900, 6);
    }

    protected UUID publishedDeparture(LocalDate start, long pricePaise, int maxGroupSize) throws Exception {
        String admin = adminToken();
        UUID id = createDraft(admin, createTrack(admin, 2), guideUser(), start, pricePaise, maxGroupSize);
        authed(post("/api/admin/departures/" + id + "/publish"), admin, null).andExpect(status().isOk());
        return id;
    }

    /** A trekker who may book: verified phone and an emergency contact. Returns the access token. */
    protected String bookingTrekker() throws Exception {
        String email = uniqueEmail();
        String token = emailTrekker(email);
        UUID id = userIdByEmail(email);
        jdbc.update("UPDATE users SET phone = ?, phone_verified_at = now() WHERE id = ?", uniquePhone(), id);
        jdbc.update("""
                INSERT INTO trekker_profiles (user_id, emergency_name, emergency_relation, emergency_phone, created_at, updated_at)
                VALUES (?, 'Meera Rao', 'Sister', '+919812345678', now(), now())""", id);
        return token;
    }

    protected UUID userIdByEmail(String email) {
        return jdbc.queryForObject("SELECT id FROM users WHERE email = ?", UUID.class, email);
    }

    protected static String travellersJson(int count) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < count; i++) {
            if (i > 0) sb.append(',');
            sb.append("""
                    {"full_name":"Traveller %d","date_of_birth":"1995-04-12","gender":"FEMALE"}""".formatted(i));
        }
        return sb.append(']').toString();
    }

    protected ResultActions requestHold(String token, UUID departureId, int seats) throws Exception {
        return authed(post("/api/trekker/bookings"), token, """
                {"departure_id":"%s","seats":%d,"travellers":%s}""".formatted(departureId, seats, travellersJson(seats)));
    }

    /** Holds seats and returns the booking id. */
    protected UUID hold(String token, UUID departureId, int seats) throws Exception {
        MvcResult result = requestHold(token, departureId, seats).andExpect(status().isCreated()).andReturn();
        return UUID.fromString(JsonPath.read(result.getResponse().getContentAsString(), "$.id"));
    }

    /** Creates the Razorpay order; returns [payment id, razorpay order id]. */
    protected String[] order(String token, UUID bookingId) throws Exception {
        MvcResult result = authed(post("/api/trekker/payments/orders"), token, """
                {"booking_id":"%s"}""".formatted(bookingId)).andExpect(status().isCreated()).andReturn();
        String body = result.getResponse().getContentAsString();
        return new String[]{JsonPath.read(body, "$.payment_id"), JsonPath.read(body, "$.razorpay_order_id")};
    }

    protected ResultActions verifyPayment(String token, String paymentId, String orderId, String razorpayPaymentId)
            throws Exception {
        String signature = HashingUtils.hmacSha256Hex(FakePaymentGateway.KEY_SECRET, orderId + "|" + razorpayPaymentId);
        return authed(post("/api/trekker/payments/" + paymentId + "/verify"), token, """
                {"razorpay_order_id":"%s","razorpay_payment_id":"%s","razorpay_signature":"%s"}"""
                .formatted(orderId, razorpayPaymentId, signature));
    }

    /** Hold, order, capture and verify. Returns the booking id. */
    protected UUID confirmedBooking(String token, UUID departureId, int seats) throws Exception {
        UUID bookingId = hold(token, departureId, seats);
        String[] order = order(token, bookingId);
        verifyPayment(token, order[0], order[1], gateway.capture(order[1]).id())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAID"));
        return bookingId;
    }

    protected String bookingStatus(UUID bookingId) {
        return jdbc.queryForObject("SELECT status FROM bookings WHERE id = ?", String.class, bookingId);
    }

    protected int seatsTaken(UUID departureId) {
        return jdbc.queryForObject("SELECT seats_taken FROM departures WHERE id = ?", Integer.class, departureId);
    }

    protected ResultActions webhook(String json, String eventId) throws Exception {
        return mockMvc.perform(post("/api/webhooks/razorpay")
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Razorpay-Signature", HashingUtils.hmacSha256Hex(WEBHOOK_SECRET, json))
                .header("X-Razorpay-Event-Id", eventId)
                .content(json));
    }

    protected static String paymentEvent(String event, String orderId, String paymentId, String status, long amount) {
        return """
                {"entity":"event","event":"%s","payload":{"payment":{"entity":{"id":"%s","order_id":"%s",
                "status":"%s","amount":%d,"currency":"INR","method":"netbanking","bank":"HDFC",
                "error_code":%s,"error_description":%s}}}}"""
                .formatted(event, paymentId, orderId, status, amount,
                        "failed".equals(status) ? "\"BAD_REQUEST_ERROR\"" : "null",
                        "failed".equals(status) ? "\"Bank declined\"" : "null");
    }

    protected static String refundEvent(String event, String refundId, String paymentId, long amount, String status) {
        return """
                {"entity":"event","event":"%s","payload":{"refund":{"entity":{"id":"%s","payment_id":"%s",
                "amount":%d,"status":"%s","notes":{}}}}}""".formatted(event, refundId, paymentId, amount, status);
    }
}
