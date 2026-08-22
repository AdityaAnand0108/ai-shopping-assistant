package com.shopassist.config.security;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * JWT signing and lifetime settings.
 *
 * @param secret        HMAC signing key; must be at least 32 bytes for HS256
 * @param accessTokenTtl how long an issued token stays valid
 * @param issuer        the {@code iss} claim, verified on every request
 */
@ConfigurationProperties(prefix = "shopassist.jwt")
public record JwtProperties(
        String secret,
        Duration accessTokenTtl,
        String issuer
) {
    /**
     * The checked-in development key. Present so the project runs from a clean
     * clone with no setup, and recognised by name at startup so that running
     * with it produces a loud warning.
     */
    public static final String DEVELOPMENT_SECRET =
            "dev-only-insecure-signing-key-replace-me-in-any-real-deployment";

    public JwtProperties {
        if (secret == null || secret.isBlank()) {
            secret = DEVELOPMENT_SECRET;
        }
        if (accessTokenTtl == null) {
            accessTokenTtl = Duration.ofMinutes(60);
        }
        if (issuer == null || issuer.isBlank()) {
            issuer = "shop-assistant";
        }
    }

    public boolean usingDevelopmentSecret() {
        return DEVELOPMENT_SECRET.equals(secret);
    }
}
