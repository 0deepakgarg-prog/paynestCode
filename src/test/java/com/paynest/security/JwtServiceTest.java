package com.paynest.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JwtServiceTest {

    private static final String SECRET = "12345678901234567890123456789012";

    private final JwtService jwtService = new JwtService(SECRET, 3_600_000L, 300_000L);

    @Test
    void isTokenValidShouldReturnTrueForFreshToken() {
        String token = jwtService.generateToken("acc-1", "PIN", "tenant-1", "SUBSCRIBER");

        assertTrue(jwtService.isTokenValid(token));
    }

    @Test
    void isTokenValidShouldReturnFalseForExpiredToken() {
        Date issuedAt = new Date(System.currentTimeMillis() - 60_000L);
        Date expiry = new Date(System.currentTimeMillis() - 1_000L);

        String token = buildToken(issuedAt, expiry);

        assertFalse(jwtService.isTokenValid(token));
    }

    @Test
    void isTokenValidShouldReturnFalseWhenIssuedAtIsInFuture() {
        Date issuedAt = new Date(System.currentTimeMillis() + 60_000L);
        Date expiry = new Date(System.currentTimeMillis() + 120_000L);

        String token = buildToken(issuedAt, expiry);

        assertFalse(jwtService.isTokenValid(token));
    }

    private String buildToken(Date issuedAt, Date expiry) {
        SecretKey signingKey = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));

        return Jwts.builder()
                .setSubject("acc-1")
                .setIssuedAt(issuedAt)
                .setExpiration(expiry)
                .claim("tenant", "tenant-1")
                .claim("scope", "SUBSCRIBER")
                .claim("authType", "PIN")
                .signWith(signingKey, SignatureAlgorithm.HS256)
                .compact();
    }
}
