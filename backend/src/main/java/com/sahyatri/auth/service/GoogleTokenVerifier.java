package com.sahyatri.auth.service;

import com.sahyatri.auth.dto.GoogleIdentity;
import com.sahyatri.common.config.AuthProperties;
import com.sahyatri.common.exception.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Component;

import java.util.Set;

/** Verifies Google Identity Services ID tokens against Google's published keys. */
@Component
public class GoogleTokenVerifier {

    private static final String JWK_SET_URI = "https://www.googleapis.com/oauth2/v3/certs";
    private static final Set<String> ISSUERS = Set.of("accounts.google.com", "https://accounts.google.com");

    private final String clientId;
    private volatile NimbusJwtDecoder decoder;

    public GoogleTokenVerifier(AuthProperties props) {
        this.clientId = props.googleClientId();
    }

    public GoogleIdentity verify(String idToken) {
        if (clientId == null || clientId.isBlank()) {
            throw invalid("Google sign-in is not configured");
        }
        Jwt jwt;
        try {
            jwt = decoder().decode(idToken);
        } catch (JwtException e) {
            throw invalid("Google sign-in failed, please try again");
        }
        return new GoogleIdentity(
                jwt.getSubject(),
                jwt.getClaimAsString("email"),
                Boolean.TRUE.equals(jwt.getClaimAsBoolean("email_verified")),
                jwt.getClaimAsString("name"));
    }

    private NimbusJwtDecoder decoder() {
        if (decoder == null) {
            synchronized (this) {
                if (decoder == null) {
                    NimbusJwtDecoder d = NimbusJwtDecoder.withJwkSetUri(JWK_SET_URI).build();
                    OAuth2TokenValidator<Jwt> issuer = jwt -> ISSUERS.contains(jwt.getClaimAsString("iss"))
                            ? OAuth2TokenValidatorResult.success()
                            : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "bad issuer", null));
                    OAuth2TokenValidator<Jwt> audience = jwt -> jwt.getAudience() != null && jwt.getAudience().contains(clientId)
                            ? OAuth2TokenValidatorResult.success()
                            : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "bad audience", null));
                    d.setJwtValidator(new DelegatingOAuth2TokenValidator<>(JwtValidators.createDefault(), issuer, audience));
                    decoder = d;
                }
            }
        }
        return decoder;
    }

    private static ApiException invalid(String message) {
        return new ApiException(HttpStatus.UNAUTHORIZED, "GOOGLE_TOKEN_INVALID", message);
    }
}
