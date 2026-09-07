package com.mediwise.common.util;

import io.jsonwebtoken.*;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Component
public class JwtUtil {

    @Value("${application.jwt.secret}")
    private String secret;

    @Value("${application.jwt.expiration}")
    private long accessExpiration;

    @Value("${application.jwt.refresh-expiration}")
    private long refreshExpiration;

    private SecretKey getSignKey() {
        byte[] keyBytes;
        try {
            keyBytes = Decoders.BASE64.decode(secret);
        } catch (Exception e) {
            keyBytes = secret.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        }
        if (keyBytes.length < 32) {
            throw new IllegalStateException(
                    "application.jwt.secret is too short (" + keyBytes.length +
                            " bytes). It must decode to at least 32 bytes (256 bits) for HS256.");
        }
        return Keys.hmacShaKeyFor(keyBytes);
    }
    public String generateAccessToken(String subject, Map<String, Object> claims) {
        return buildToken(subject, claims, accessExpiration);
    }

    public String generateRefreshToken(String subject) {
        return buildToken(subject, Map.of("type", "refresh"), refreshExpiration);
    }

    private String buildToken(String subject, Map<String, Object> claims, long expiration) {
        var builder = Jwts.builder();
        if (claims != null && !claims.isEmpty()) {
            builder.claims(claims);
        }
        return builder
                .id(UUID.randomUUID().toString())
                .subject(subject)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expiration))
                .signWith(getSignKey())
                .compact();
    }

    public boolean isTokenValid(String token) {
        try {
            parseClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            log.warn("Invalid JWT: {}", e.getMessage());
            return false;
        }
    }

    public String extractSubject(String token) {
        return parseClaims(token).getPayload().getSubject();
    }

    public String extractJti(String token) {
        return parseClaims(token).getPayload().getId();
    }

    public Date extractExpiration(String token) {
        return parseClaims(token).getPayload().getExpiration();
    }

    public Object extractClaim(String token, String claimKey) {
        return parseClaims(token).getPayload().get(claimKey);
    }
    public boolean isRefreshToken(String token) {
        Object type = extractClaim(token, "type");
        return "refresh".equals(type);
    }

    public boolean isAccessToken(String token) {
        return !isRefreshToken(token);
    }

    private Jws<Claims> parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSignKey())
                .build()
                .parseSignedClaims(token);
    }
}
