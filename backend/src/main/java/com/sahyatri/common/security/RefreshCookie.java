package com.sahyatri.common.security;

import com.sahyatri.common.config.AuthProperties;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;

/** Builds the httpOnly refresh-token cookie. Scoped to /api/auth so it never rides along on other calls. */
@Component
public class RefreshCookie {

    public static final String NAME = "sahyatri_refresh";

    private final AuthProperties props;

    public RefreshCookie(AuthProperties props) {
        this.props = props;
    }

    public String set(String token) {
        return build(token, props.refreshTtl());
    }

    public String clear() {
        return build("", Duration.ZERO);
    }

    private String build(String value, Duration maxAge) {
        return ResponseCookie.from(NAME, value)
                .httpOnly(true)
                .secure(props.cookieSecure())
                .sameSite("Lax")
                .path("/api/auth")
                .maxAge(maxAge)
                .build()
                .toString();
    }
}
