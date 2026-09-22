package com.sahyatri.common.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Locale;

/** 401 for missing/invalid tokens; TOKEN_EXPIRED tells the frontend to refresh and retry. */
@Component
public class JsonAuthenticationEntryPoint implements AuthenticationEntryPoint {

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException ex)
            throws IOException {
        if (isExpired(ex)) {
            SecurityErrorWriter.write(response, HttpServletResponse.SC_UNAUTHORIZED, "TOKEN_EXPIRED", "Access token expired");
        } else {
            SecurityErrorWriter.write(response, HttpServletResponse.SC_UNAUTHORIZED, "UNAUTHENTICATED", "Please sign in");
        }
    }

    private static boolean isExpired(AuthenticationException ex) {
        for (Throwable t = ex; t != null; t = t.getCause()) {
            if (t.getMessage() != null && t.getMessage().toLowerCase(Locale.ROOT).contains("expired")) {
                return true;
            }
        }
        return false;
    }
}
