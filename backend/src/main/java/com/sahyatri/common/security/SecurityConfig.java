package com.sahyatri.common.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

    private final JsonAuthenticationEntryPoint authenticationEntryPoint;
    private final JsonAccessDeniedHandler accessDeniedHandler;

    public SecurityConfig(JsonAuthenticationEntryPoint authenticationEntryPoint,
                          JsonAccessDeniedHandler accessDeniedHandler) {
        this.authenticationEntryPoint = authenticationEntryPoint;
        this.accessDeniedHandler = accessDeniedHandler;
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        // CSRF tokens are off on purpose (CodeQL java/spring-disabled-csrf-protection is dismissed as won't fix).
        // Everything but /api/auth/refresh and /logout authenticates with a Bearer header, which a browser never
        // attaches on its own. Those two read the httpOnly SameSite=Lax refresh cookie (path /api/auth), so other
        // sites don't get it on a POST, and the CORS filter 403s any request whose Origin isn't in
        // CORS_ALLOWED_ORIGINS before it reaches them (AuthControllerTests.cookieEndpointsRejectOtherOrigins).
        return http
                .csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/auth/me").authenticated()
                        .requestMatchers("/api/public/**", "/api/auth/**", "/actuator/health", "/actuator/health/**", "/error").permitAll()
                        // Crawlers read this on the API host before fetching the sitemap (docs/TRD.md §7.16).
                        .requestMatchers("/robots.txt").permitAll()
                        // Razorpay calls these; the HMAC signature is checked before anything is parsed.
                        .requestMatchers("/api/webhooks/**").permitAll()
                        .requestMatchers("/api/trekker/**").hasRole("TREKKER")
                        .requestMatchers("/api/guide/**").hasRole("GUIDE")
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        .anyRequest().authenticated())
                .oauth2ResourceServer(rs -> rs
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter()))
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .build();
    }

    /** Maps the JWT `role` claim to ROLE_* authorities for hasRole(). */
    private static JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter authorities = new JwtGrantedAuthoritiesConverter();
        authorities.setAuthoritiesClaimName("role");
        authorities.setAuthorityPrefix("ROLE_");
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authorities);
        return converter;
    }
}
