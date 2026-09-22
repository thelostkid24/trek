package com.sahyatri.auth;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.web.servlet.ResultActions;

import static org.mockito.ArgumentMatchers.eq;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class OtpFlowTests extends AuthTestSupport {

    @Test
    void verifyingCodeCreatesTrekkerThenLogsInExistingUser() throws Exception {
        String phone = uniquePhone();
        String code = requestAndCaptureCode(phone);

        verify(phone, code, "\"full_name\":\"Ravi K\",")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.is_new_user").value(true))
                .andExpect(jsonPath("$.user.phone").value(phone))
                .andExpect(jsonPath("$.user.full_name").value("Ravi K"))
                .andExpect(jsonPath("$.user.phone_verified").value(true))
                .andExpect(jsonPath("$.user.auth_methods[0]").value("PHONE_OTP"));

        // Same code can't be used twice.
        verify(phone, code, "").andExpect(status().isGone());

        jdbc.update("UPDATE otp_challenges SET created_at = created_at - interval '1 minute' WHERE phone = ?", phone);
        String second = requestAndCaptureCode(phone);
        verify(phone, second, "")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.is_new_user").value(false));
    }

    @Test
    void requestReturnsTimingsAndIsRateLimited() throws Exception {
        String phone = uniquePhone();
        request(phone)
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.expires_in").value(300))
                .andExpect(jsonPath("$.resend_after").value(30));
        request(phone)
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("OTP_RATE_LIMITED"))
                .andExpect(jsonPath("$.details.retry_after").isNumber());
    }

    @Test
    void wrongCodesCountDownThenLockOut() throws Exception {
        String phone = uniquePhone();
        String code = requestAndCaptureCode(phone);
        String wrong = code.equals("000000") ? "111111" : "000000";

        verify(phone, wrong, "")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("OTP_INVALID"))
                .andExpect(jsonPath("$.details.attempts_left").value(4));
        for (int i = 0; i < 4; i++) {
            verify(phone, wrong, "").andExpect(status().isBadRequest());
        }
        verify(phone, code, "")
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("OTP_TOO_MANY_ATTEMPTS"));
    }

    @Test
    void expiredCodeIsRejected() throws Exception {
        String phone = uniquePhone();
        String code = requestAndCaptureCode(phone);
        jdbc.update("UPDATE otp_challenges SET expires_at = now() - interval '1 second' WHERE phone = ?", phone);

        verify(phone, code, "")
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.code").value("OTP_EXPIRED"));
    }

    @Test
    void nonIndianNumberFailsValidation() throws Exception {
        request("+14155550123")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.fields.phone").exists());
    }

    private ResultActions request(String phone) throws Exception {
        return postJson("/api/auth/otp/request", """
                {"phone":"%s"}""".formatted(phone));
    }

    private ResultActions verify(String phone, String code, String extraFields) throws Exception {
        return postJson("/api/auth/otp/verify", """
                {%s"phone":"%s","code":"%s"}""".formatted(extraFields, phone, code));
    }

    private String requestAndCaptureCode(String phone) throws Exception {
        request(phone).andExpect(status().isAccepted());
        ArgumentCaptor<String> code = ArgumentCaptor.forClass(String.class);
        org.mockito.Mockito.verify(smsSender, org.mockito.Mockito.atLeastOnce()).sendOtp(eq(phone), code.capture());
        return code.getValue();
    }
}
