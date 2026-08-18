package com.schwab.audit.security;

import com.schwab.audit.entity.User;
import com.schwab.audit.util.Constants;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;

/**
 * Service for JWT token generation, validation, and claims extraction.
 * 
 * Provides stateless JWT-based authentication without session management.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class JwtService {

    @Value("${app.jwt.secret}")
    private String jwtSecret;

    @Value("${app.jwt.expiry-hours:24}")
    private long jwtExpiryHours;

    @Value("${app.jwt.algorithm:HS256}")
    private String jwtAlgorithm;

    /**
     * Generates a new JWT token for the authenticated user.
     * 
     * @param user the authenticated user
     * @return JWT token string
     */
    public String generateToken(User user) {
        try {
            Instant now = Instant.now();
            Instant expiryTime = now.plus(jwtExpiryHours, ChronoUnit.HOURS);

            SecretKey key = getSigningKey();

            return Jwts.builder()
                    .subject(user.getUsername())
                    .claim(Constants.JWT_CLAIM_ROLE, user.getRole().name())
                    .claim("userId", user.getId())
                    .issuedAt(Date.from(now))
                    .expiration(Date.from(expiryTime))
                    .signWith(key, SignatureAlgorithm.HS256)
                    .compact();
        } catch (Exception e) {
            log.error("Error generating JWT token", e);
            throw new RuntimeException("Failed to generate JWT token", e);
        }
    }

    /**
     * Validates a JWT token and returns true if valid.
     * 
     * @param token the JWT token to validate
     * @return true if token is valid and not expired
     */
    public boolean validateToken(String token) {
        try {
            if (token == null || token.isEmpty()) {
                return false;
            }

            SecretKey key = getSigningKey();
            Jwts.parserBuilder()
                    .setSigningKey(key)
                    .build()
                    .parseClaimsJws(token);

            return true;
        } catch (ExpiredJwtException e) {
            log.debug("JWT token has expired");
            return false;
        } catch (UnsupportedJwtException e) {
            log.debug("JWT token is unsupported");
            return false;
        } catch (MalformedJwtException e) {
            log.debug("Invalid JWT token");
            return false;
        } catch (SignatureException e) {
            log.debug("Invalid JWT signature");
            return false;
        } catch (IllegalArgumentException e) {
            log.debug("JWT claims string is empty");
            return false;
        }
    }

    /**
     * Extracts the username from a valid JWT token.
     * 
     * @param token the JWT token
     * @return the username
     * @throws IllegalArgumentException if token is invalid
     */
    public String extractUsername(String token) {
        try {
            SecretKey key = getSigningKey();
            return Jwts.parserBuilder()
                    .setSigningKey(key)
                    .build()
                    .parseClaimsJws(token)
                    .getBody()
                    .getSubject();
        } catch (JwtException e) {
            throw new IllegalArgumentException("Invalid JWT token", e);
        }
    }

    /**
     * Extracts the role from a valid JWT token.
     * 
     * @param token the JWT token
     * @return the role name
     * @throws IllegalArgumentException if token is invalid
     */
    public String extractRole(String token) {
        try {
            SecretKey key = getSigningKey();
            return Jwts.parserBuilder()
                    .setSigningKey(key)
                    .build()
                    .parseClaimsJws(token)
                    .getBody()
                    .get(Constants.JWT_CLAIM_ROLE, String.class);
        } catch (JwtException e) {
            throw new IllegalArgumentException("Invalid JWT token", e);
        }
    }

    /**
     * Extracts the expiry time from a valid JWT token.
     * 
     * @param token the JWT token
     * @return expiry time in seconds from now (or negative if expired)
     */
    public long extractExpirySeconds(String token) {
        try {
            SecretKey key = getSigningKey();
            Date expiry = Jwts.parserBuilder()
                    .setSigningKey(key)
                    .build()
                    .parseClaimsJws(token)
                    .getBody()
                    .getExpiration();

            return (expiry.getTime() - System.currentTimeMillis()) / 1000;
        } catch (JwtException e) {
            throw new IllegalArgumentException("Invalid JWT token", e);
        }
    }

    /**
     * Gets the signing key for JWT operations.
     * Uses HMAC-SHA256 algorithm.
     * 
     * @return the SecretKey for signing/validation
     */
    private SecretKey getSigningKey() {
        byte[] keyBytes = jwtSecret.getBytes(StandardCharsets.UTF_8);
        return Keys.hmacShaKeyFor(keyBytes);
    }
}
