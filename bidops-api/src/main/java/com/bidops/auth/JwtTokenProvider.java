package com.bidops.auth;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Component
public class JwtTokenProvider {

    @Value("${bidops.security.jwt-secret}")
    private String secret;

    @Value("${bidops.security.jwt-expiry-ms}")
    private long expiryMs;

    private SecretKey key() {
        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < 32) {
            byte[] padded = new byte[32];
            System.arraycopy(keyBytes, 0, padded, 0, keyBytes.length);
            return Keys.hmacShaKeyFor(padded);
        }
        return Keys.hmacShaKeyFor(keyBytes);
    }

    public String generate(String userId, String email, String name) {
        Date now = new Date();
        return Jwts.builder()
                .subject(userId)
                .claim("email", email)
                .claim("name", name)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + expiryMs))
                .signWith(key())
                .compact();
    }

    public Claims parse(String token) {
        return Jwts.parser()
                .verifyWith(key())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public boolean validate(String token) {
        try {
            parse(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    public String getUserId(String token) {
        return parse(token).getSubject();
    }
}
