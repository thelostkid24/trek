package com.sahyatri.account;

import com.sahyatri.auth.AuthTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;

import static org.hamcrest.Matchers.hasItem;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PhoneChangeTests extends AuthTestSupport {

    @Test
    void emailTrekkerAddsPhoneAndCanThenSignInWithIt() throws Exception {
        String token = emailTrekker(uniqueEmail());
        String phone = uniquePhone();

        requestCode(token, phone)
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.expires_in").value(300))
                .andExpect(jsonPath("$.resend_after").value(30));
        verifyCode(token, phone, lastOtp(phone))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phone").value(phone))
                .andExpect(jsonPath("$.phone_verified").value(true))
                .andExpect(jsonPath("$.auth_methods", hasItem("PHONE_OTP")));

        requestCode(token, phone)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PHONE_ALREADY_VERIFIED"));

        // The phone now signs in to the same account.
        jdbc.update("UPDATE otp_challenges SET created_at = created_at - interval '1 minute' WHERE phone = ?", phone);
        postJson("/api/auth/otp/request", """
                {"phone":"%s"}""".formatted(phone)).andExpect(status().isAccepted());
        postJson("/api/auth/otp/verify", """
                {"phone":"%s","code":"%s"}""".formatted(phone, lastOtp(phone)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.is_new_user").value(false))
                .andExpect(jsonPath("$.user.full_name").value("Asha Rao"));
    }

    @Test
    void codesAreScopedToTheirPurpose() throws Exception {
        String token = emailTrekker(uniqueEmail());
        String phone = uniquePhone();

        // A sign-in code can't attach the phone...
        postJson("/api/auth/otp/request", """
                {"phone":"%s"}""".formatted(phone)).andExpect(status().isAccepted());
        verifyCode(token, phone, lastOtp(phone))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.code").value("OTP_EXPIRED"));

        // ...and a phone-change code can't sign in: it is checked against the pending sign-in code instead.
        String signInCode = lastOtp(phone);
        jdbc.update("UPDATE otp_challenges SET created_at = created_at - interval '1 minute' WHERE phone = ?", phone);
        requestCode(token, phone).andExpect(status().isAccepted());
        String changeCode = lastOtp(phone);
        assumeTrue(!changeCode.equals(signInCode));
        postJson("/api/auth/otp/verify", """
                {"phone":"%s","code":"%s"}""".formatted(phone, changeCode))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("OTP_INVALID"));
    }

    @Test
    void phoneOfAnotherAccountIsRejected() throws Exception {
        String taken = uniquePhone();
        phoneTrekker(taken);
        String token = emailTrekker(uniqueEmail());

        requestCode(token, taken)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PHONE_ALREADY_REGISTERED"));
        verifyCode(token, taken, "123456")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PHONE_ALREADY_REGISTERED"));
    }

    @Test
    void wrongCodeAndBadInputAreReported() throws Exception {
        String token = emailTrekker(uniqueEmail());
        String phone = uniquePhone();
        requestCode(token, phone).andExpect(status().isAccepted());
        String wrong = lastOtp(phone).equals("000000") ? "111111" : "000000";

        verifyCode(token, phone, wrong)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("OTP_INVALID"))
                .andExpect(jsonPath("$.details.attempts_left").value(4));
        requestCode(token, "+14155550123")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.fields.phone").exists());
        verifyCode(token, phone, "12")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.fields.code").exists());
    }

    private ResultActions requestCode(String token, String phone) throws Exception {
        return authed(post("/api/account/phone/otp"), token, """
                {"phone":"%s"}""".formatted(phone));
    }

    private ResultActions verifyCode(String token, String phone, String code) throws Exception {
        return authed(post("/api/account/phone/verify"), token, """
                {"phone":"%s","code":"%s"}""".formatted(phone, code));
    }
}
