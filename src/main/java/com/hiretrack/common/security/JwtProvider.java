package com.hiretrack.common.security;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.Date;
import java.util.Optional;

import javax.crypto.SecretKey;

import org.springframework.stereotype.Component;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

@Component
public class JwtProvider {

    private static final String SCOPE_CLAIM = "scope";

    private final SecretKey key;
    private final JwtProperties properties;
    private final Clock clock;

    public JwtProvider(JwtProperties properties, Clock clock) {
        this.key = Keys.hmacShaKeyFor(properties.secret().getBytes(StandardCharsets.UTF_8));
        this.properties = properties;
        this.clock = clock;
    }

    public String createAccessToken(Long userId) {
        return create(userId, TokenScope.APP, properties.accessTokenValidity());
    }

    /** 확장 프로그램 전용 토큰 — 범위를 좁힌 대신 자주 다시 로그인하지 않도록 유효기간은 길게 */
    public String createExtensionToken(Long userId) {
        return create(userId, TokenScope.EXTENSION, properties.extensionTokenValidity());
    }

    private String create(Long userId, TokenScope scope, Duration validity) {
        Date now = Date.from(clock.instant());
        Date expiresAt = Date.from(clock.instant().plus(validity));
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim(SCOPE_CLAIM, scope.name())
                .issuedAt(now)
                .expiration(expiresAt)
                .signWith(key)
                .compact();
    }

    /** 유효한 토큰이면 사용자 ID와 권한 범위를, 위조·만료된 토큰이면 빈 값을 돌려준다. */
    public Optional<TokenPrincipal> parse(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .clock(() -> Date.from(clock.instant()))
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return Optional.of(new TokenPrincipal(Long.valueOf(claims.getSubject()),
                    TokenScope.fromClaim(claims.get(SCOPE_CLAIM, String.class))));
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    public record TokenPrincipal(Long userId, TokenScope scope) {
    }
}
