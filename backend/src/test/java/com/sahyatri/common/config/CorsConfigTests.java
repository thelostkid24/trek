package com.sahyatri.common.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.DefaultCorsProcessor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CorsConfigTests {

    private static final String ORIGIN = "https://theemptyvalley.com";

    private final CorsConfigurationSource source = new CorsConfig().corsConfigurationSource(List.of(ORIGIN));

    @Test
    void preflightFromTheFrontendWithItsHeadersIsAllowed() throws Exception {
        MockHttpServletResponse response = preflight(ORIGIN, "authorization, content-type");
        assertThat(response.getHeader("Access-Control-Allow-Origin")).isEqualTo(ORIGIN);
        assertThat(response.getHeader("Access-Control-Allow-Credentials")).isEqualTo("true");
    }

    @Test
    void unknownOriginOrHeaderIsRefused() throws Exception {
        assertThat(preflight("https://evil.example", "authorization").getHeader("Access-Control-Allow-Origin")).isNull();
        assertThat(preflight(ORIGIN, "x-custom").getHeader("Access-Control-Allow-Origin")).isNull();
    }

    @Test
    void wildcardOriginIsRejectedAtStartup() {
        assertThatThrownBy(() -> new CorsConfig().corsConfigurationSource(List.of("*")))
                .isInstanceOf(IllegalStateException.class);
    }

    private MockHttpServletResponse preflight(String origin, String headers) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("OPTIONS", "/api/trekker/bookings");
        request.addHeader("Origin", origin);
        request.addHeader("Access-Control-Request-Method", "POST");
        request.addHeader("Access-Control-Request-Headers", headers);
        MockHttpServletResponse response = new MockHttpServletResponse();
        CorsConfiguration config = source.getCorsConfiguration(request);
        new DefaultCorsProcessor().processRequest(config, request, response);
        return response;
    }
}
