package com.sahyatri.profile;

import com.sahyatri.auth.AuthTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TrekkerProfileTests extends AuthTestSupport {

    private static final String PROFILE = "/api/trekker/profile";

    @Autowired
    JwtEncoder jwtEncoder;

    @Test
    void newTrekkerGetsEmptyProfileWithCompletion() throws Exception {
        String token = emailTrekker(uniqueEmail());

        authed(get(PROFILE), token, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.full_name").value("Asha Rao"))
                .andExpect(jsonPath("$.avatar_url").value(nullValue()))
                .andExpect(jsonPath("$.date_of_birth").value(nullValue()))
                .andExpect(jsonPath("$.emergency_contact").value(nullValue()))
                .andExpect(jsonPath("$.updated_at").value(nullValue()))
                .andExpect(jsonPath("$.completion.percent").value(8))
                .andExpect(jsonPath("$.completion.missing").value(contains(
                        "avatar", "date_of_birth", "gender", "home_city", "experience_level",
                        "emergency_contact", "blood_group", "height_weight", "diet",
                        "phone_verified", "email_verified")));
    }

    @Test
    void putReplacesProfileAndUpdatesName() throws Exception {
        String token = emailTrekker(uniqueEmail());

        authed(put(PROFILE), token, fullProfile("  Asha R  ", "1995-04-12", uniquePhone()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.full_name").value("Asha R"))
                .andExpect(jsonPath("$.date_of_birth").value("1995-04-12"))
                .andExpect(jsonPath("$.gender").value("FEMALE"))
                .andExpect(jsonPath("$.home_city").value("Pune"))
                .andExpect(jsonPath("$.experience_level").value("INTERMEDIATE"))
                .andExpect(jsonPath("$.bio").value("Weekend trekker"))
                .andExpect(jsonPath("$.emergency_contact.name").value("Meera Rao"))
                .andExpect(jsonPath("$.emergency_contact.relation").value("Sister"))
                .andExpect(jsonPath("$.blood_group").value("O+"))
                .andExpect(jsonPath("$.medical_notes").value("Mild asthma"))
                .andExpect(jsonPath("$.highest_altitude_m").value(3800))
                .andExpect(jsonPath("$.height_cm").value(162))
                .andExpect(jsonPath("$.weight_kg").value(58))
                .andExpect(jsonPath("$.allergies").value("Peanuts"))
                .andExpect(jsonPath("$.diet").value("VEGETARIAN"))
                .andExpect(jsonPath("$.shoe_size_uk").value(6))
                .andExpect(jsonPath("$.updated_at").isString())
                .andExpect(jsonPath("$.completion.percent").value(75))
                .andExpect(jsonPath("$.completion.missing").value(contains("avatar", "phone_verified", "email_verified")));

        authed(get("/api/auth/me"), token, null)
                .andExpect(jsonPath("$.full_name").value("Asha R"));

        // Full replacement: omitted fields are cleared, blank strings become null.
        authed(put(PROFILE), token, """
                {"full_name":"Asha R","home_city":"   "}""")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.home_city").value(nullValue()))
                .andExpect(jsonPath("$.gender").value(nullValue()))
                .andExpect(jsonPath("$.emergency_contact").value(nullValue()))
                .andExpect(jsonPath("$.medical_notes").value(nullValue()))
                .andExpect(jsonPath("$.height_cm").value(nullValue()))
                .andExpect(jsonPath("$.diet").value(nullValue()));
    }

    @Test
    void validationUsesSnakeCaseAndNestedKeys() throws Exception {
        String token = emailTrekker(uniqueEmail());

        authed(put(PROFILE), token, """
                {"full_name":"","bio":"%s","blood_group":"C+",
                 "emergency_contact":{"name":"Meera","relation":"","phone":"12345"}}""".formatted("x".repeat(501)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.details.fields.full_name").exists())
                .andExpect(jsonPath("$.details.fields.bio").exists())
                .andExpect(jsonPath("$.details.fields.blood_group").exists())
                .andExpect(jsonPath("$.details.fields['emergency_contact.relation']").exists())
                .andExpect(jsonPath("$.details.fields['emergency_contact.phone']").exists());

        authed(put(PROFILE), token, """
                {"full_name":"Asha","gender":"ROBOT"}""")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.details.fields.gender").exists());

        authed(put(PROFILE), token, """
                {"full_name":"Asha","date_of_birth":"1995-13-40"}""")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.fields.date_of_birth").exists());

        authed(put(PROFILE), token, """
                {"full_name":"Asha","height_cm":40,"weight_kg":400,"highest_altitude_m":9000,
                 "shoe_size_uk":20,"allergies":"%s"}""".formatted("x".repeat(301)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.fields.height_cm").exists())
                .andExpect(jsonPath("$.details.fields.weight_kg").exists())
                .andExpect(jsonPath("$.details.fields.highest_altitude_m").exists())
                .andExpect(jsonPath("$.details.fields.shoe_size_uk").exists())
                .andExpect(jsonPath("$.details.fields.allergies").exists());

        authed(put(PROFILE), token, """
                {"full_name":"Asha","diet":"KETO"}""")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.fields.diet").exists());
    }

    @Test
    void ageMustBeBetween18And100() throws Exception {
        String token = emailTrekker(uniqueEmail());
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Kolkata"));

        String tooYoung = today.minusYears(18).plusDays(1).toString();
        authed(put(PROFILE), token, fullProfile("Asha", tooYoung, uniquePhone()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.fields.date_of_birth").exists());

        authed(put(PROFILE), token, fullProfile("Asha", today.minusYears(18).toString(), uniquePhone()))
                .andExpect(status().isOk());

        authed(put(PROFILE), token, fullProfile("Asha", today.minusYears(101).toString(), uniquePhone()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.fields.date_of_birth").exists());
    }

    @Test
    void emergencyContactCannotBeOwnPhone() throws Exception {
        String phone = uniquePhone();
        String token = accessToken(phoneTrekker(phone));

        authed(put(PROFILE), token, fullProfile("Ravi", "1990-01-01", phone))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.fields['emergency_contact.phone']").exists());
    }

    @Test
    void phoneSignupWithoutNameMustSendName() throws Exception {
        String token = accessToken(phoneTrekker(uniquePhone()));

        authed(get(PROFILE), token, null)
                .andExpect(jsonPath("$.full_name").value(nullValue()))
                .andExpect(jsonPath("$.completion.missing[0]").value("full_name"));
        authed(put(PROFILE), token, "{}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.fields.full_name").exists());
    }

    @Test
    void onlyTrekkersCanUseProfile() throws Exception {
        mockMvc.perform(get(PROFILE))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));

        authed(get(PROFILE), tokenWithRole("GUIDE"), null)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void unknownAccountIsTreatedAsSignedOut() throws Exception {
        authed(get(PROFILE), tokenWithRole("TREKKER"), null)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    private String tokenWithRole(String role) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(UUID.randomUUID().toString())
                .issuedAt(now)
                .expiresAt(now.plus(5, ChronoUnit.MINUTES))
                .claim("role", role)
                .build();
        return jwtEncoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
    }

    private static String fullProfile(String name, String dateOfBirth, String emergencyPhone) {
        return """
                {"full_name":"%s","date_of_birth":"%s","gender":"FEMALE","home_city":"Pune",
                 "experience_level":"INTERMEDIATE","bio":"Weekend trekker",
                 "emergency_contact":{"name":"Meera Rao","relation":"Sister","phone":"%s"},
                 "blood_group":"O+","medical_notes":"Mild asthma","highest_altitude_m":3800,
                 "height_cm":162,"weight_kg":58,"allergies":"Peanuts","diet":"VEGETARIAN","shoe_size_uk":6}""".formatted(name, dateOfBirth, emergencyPhone);
    }
}
