package com.sahyatri.auth.service;

import com.sahyatri.auth.entity.RefreshToken;
import com.sahyatri.auth.entity.User;
import com.sahyatri.auth.repository.RefreshTokenRepository;
import com.sahyatri.common.config.AuthProperties;
import com.sahyatri.common.exception.ApiException;
import com.sahyatri.common.util.HashingUtils;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/** Issues access JWTs and manages the rotating refresh tokens behind the cookie. */
@Service
public class TokenService {

    private final JwtEncoder jwtEncoder;
    private final RefreshTokenRepository refreshTokens;
    private final AuthProperties props;

    public TokenService(JwtEncoder jwtEncoder, RefreshTokenRepository refreshTokens, AuthProperties props) {
        this.jwtEncoder = jwtEncoder;
        this.refreshTokens = refreshTokens;
        this.props = props;
    }

    public record IssuedTokens(String accessToken, String refreshToken) {
    }

    /** Result of rotating a refresh token: whose it was and the replacement raw token. */
    public record Rotation(UUID userId, String refreshToken) {
    }

    @Transactional
    public IssuedTokens issue(User user) {
        return new IssuedTokens(accessToken(user), newRefreshToken(user.getId()).raw());
    }

    public long accessTtlSeconds() {
        return props.accessTtl().toSeconds();
    }

    public String accessToken(User user) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("sahyatri")
                .subject(user.getId().toString())
                .issuedAt(now)
                .expiresAt(now.plus(props.accessTtl()))
                .claim("role", user.getRole().name())
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    /**
     * Validates and rotates a refresh token. A token rotated within the reuse grace window (e.g. two tabs
     * refreshing at once) gets a fresh sibling; reuse of an older rotated token is treated as theft and every
     * live token of that user is revoked. Tokens revoked by logout or theft response are simply rejected.
     */
    @Transactional(noRollbackFor = ApiException.class)
    public Rotation rotate(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw invalidRefresh();
        }
        RefreshToken current = refreshTokens.findByTokenHash(HashingUtils.sha256Hex(rawToken))
                .orElseThrow(TokenService::invalidRefresh);
        Instant now = Instant.now();
        if (current.getExpiresAt().isBefore(now)) {
            throw invalidRefresh();
        }
        if (current.getRevokedAt() != null) {
            if (current.getReplacedById() == null) {
                // Revoked by logout or by a theft response — never usable again.
                throw invalidRefresh();
            }
            if (current.getRevokedAt().plus(props.refreshReuseGrace()).isBefore(now)) {
                refreshTokens.revokeAllForUser(current.getUserId(), now);
                throw invalidRefresh();
            }
            return new Rotation(current.getUserId(), newRefreshToken(current.getUserId()).raw());
        }
        NewRefresh next = newRefreshToken(current.getUserId());
        current.revoke(next.id());
        return new Rotation(current.getUserId(), next.raw());
    }

    @Transactional
    public void revoke(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return;
        }
        refreshTokens.findByTokenHash(HashingUtils.sha256Hex(rawToken))
                .filter(t -> t.getRevokedAt() == null)
                .ifPresent(t -> t.revoke(null));
    }

    @Transactional
    public void revokeAll(UUID userId) {
        refreshTokens.revokeAllForUser(userId, Instant.now());
    }

    private record NewRefresh(UUID id, String raw) {
    }

    private NewRefresh newRefreshToken(UUID userId) {
        String raw = HashingUtils.randomUrlToken(32);
        RefreshToken token = refreshTokens.save(
                new RefreshToken(userId, HashingUtils.sha256Hex(raw), Instant.now().plus(props.refreshTtl())));
        return new NewRefresh(token.getId(), raw);
    }

    private static ApiException invalidRefresh() {
        return new ApiException(HttpStatus.UNAUTHORIZED, "REFRESH_TOKEN_INVALID", "Session expired, please sign in again");
    }
}
