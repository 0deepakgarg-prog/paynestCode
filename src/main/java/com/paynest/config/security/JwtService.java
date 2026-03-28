package com.paynest.config.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Date;

@Service
public class JwtService {

    private final SecretKey signingKey;
    private final long expirationMs;
    private final long challengeExpirationMs;

    public JwtService(
            @Value("${security.jwt.secret}") String secret,
            @Value("${security.jwt.expiration-ms:3600000}") long expirationMs,
            @Value("${security.jwt.challenge-expiration-ms:300000}") long challengeExpirationMs
    ) {
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationMs = expirationMs;
        this.challengeExpirationMs = challengeExpirationMs;
    }

    public String generateToken(String accountId,
                                String authType,
                                String tenant,
                                String accountType) {
        io.jsonwebtoken.JwtBuilder builder = buildToken(accountId, expirationMs)
                .claim("tenant", tenant)
                .claim("scope", accountType);

        if (authType != null && !authType.isBlank()) {
            builder.claim("authType", authType);
        }
        return builder.compact();
    }

    public String generateChallengeToken() {
            SecureRandom secureRandom = new SecureRandom();

            byte[] challengeBytes = new byte[32]; // 256 bit challenge
            secureRandom.nextBytes(challengeBytes);

            return Base64.getUrlEncoder()
                    .withoutPadding()
                    .encodeToString(challengeBytes);

    }

    public String extractAccountId(String token) {
        return extractAllClaims(token).getSubject();
    }

    public Claims getClaims(String token) {
        return extractAllClaims(token);
    }

    public String extractTenant(String token) {
        return extractAllClaims(token).get("tenant").toString();
    }

    public String extractAuthType(String token) {
        Object authType = extractAllClaims(token).get("authType");
        return authType == null ? null : authType.toString();
    }

    public boolean isTokenValid(String token) {
        try {
            Claims claims = extractAllClaims(token);
            Date now = new Date();
            Date issuedAt = claims.getIssuedAt();
            Date expiry = claims.getExpiration();

            if (issuedAt == null || expiry == null) {
                return false;
            }

            if (issuedAt.after(now)) {
                return false;
            }

            if (!expiry.after(now)) {
                return false;
            }

            return !expiry.before(issuedAt);
        } catch (Exception ex) {
            return false;
        }
    }

    public long getExpirationSeconds() {
        return expirationMs / 1000L;
    }

    public long getChallengeExpirationSeconds() {
        return challengeExpirationMs / 1000L;
    }

    private io.jsonwebtoken.JwtBuilder buildToken(String accountId, long tokenExpirationMs) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + tokenExpirationMs);

        return Jwts.builder()
                .setSubject(accountId)
                .setIssuedAt(now)
                .setExpiration(expiry)
                .signWith(signingKey, SignatureAlgorithm.HS256);
    }

    private Claims extractAllClaims(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(signingKey)
                .build()
                .parseClaimsJws(token)
                .getBody();
    }
}

