package com.shopassist.security;

import com.shopassist.config.security.JwtProperties;
import com.shopassist.entity.user.AppUser;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;
import javax.crypto.SecretKey;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Issues and verifies access tokens.
 *
 * <p>The subject is the user's {@code publicRef}, never the database primary
 * key: a token that leaks should tell an attacker nothing about how many
 * accounts exist or in what order they were created.
 *
 * <p>Each token carries a {@code jti} so {@link TokenDenylist} can revoke an
 * individual token at logout.
 */
@Service
@Slf4j
public class JwtService {

    private static final String CLAIM_USERNAME = "username";
    private static final String CLAIM_ROLE = "role";

    private final JwtProperties properties;
    private final SecretKey signingKey;

    public JwtService(JwtProperties properties) {
        this.properties = properties;
        byte[] keyBytes = properties.secret().getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < 32) {
            throw new IllegalStateException(
                    "shopassist.jwt.secret must be at least 32 bytes for HS256; got " + keyBytes.length);
        }
        this.signingKey = Keys.hmacShaKeyFor(keyBytes);
    }

    @PostConstruct
    void warnAboutDevelopmentSecret() {
        if (properties.usingDevelopmentSecret()) {
            log.warn("""
                    Signing JWTs with the built-in development key. This is fine for a local \
                    demo but anyone with the source can mint valid tokens. Set the \
                    SHOPASSIST_JWT_SECRET environment variable before exposing this anywhere.""");
        }
    }

    public IssuedToken issue(AppUser user) {
        Instant issuedAt = Instant.now();
        Instant expiresAt = issuedAt.plus(properties.accessTokenTtl());
        String tokenId = UUID.randomUUID().toString();

        String token = Jwts.builder()
                .id(tokenId)
                .issuer(properties.issuer())
                .subject(user.getPublicRef())
                .claim(CLAIM_USERNAME, user.getUsername())
                .claim(CLAIM_ROLE, user.getRole().name())
                .issuedAt(Date.from(issuedAt))
                .expiration(Date.from(expiresAt))
                .signWith(signingKey)
                .compact();

        return new IssuedToken(token, expiresAt, tokenId);
    }

    /**
     * Verifies signature, issuer and expiry.
     *
     * @return the token's claims, or empty if the token is unusable for any
     *         reason. Callers deliberately cannot tell which reason: a caller
     *         that could distinguish "expired" from "bad signature" would leak
     *         information useful for forging tokens.
     */
    public Optional<VerifiedToken> verify(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(signingKey)
                    .requireIssuer(properties.issuer())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            return Optional.of(new VerifiedToken(
                    claims.getId(),
                    claims.getSubject(),
                    claims.get(CLAIM_USERNAME, String.class),
                    claims.get(CLAIM_ROLE, String.class),
                    claims.getExpiration().toInstant()));
        } catch (JwtException | IllegalArgumentException e) {
            log.debug("Rejected token: {}", e.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    public record IssuedToken(String token, Instant expiresAt, String tokenId) {
    }

    public record VerifiedToken(String tokenId, String publicRef, String username,
                                String role, Instant expiresAt) {
    }
}
