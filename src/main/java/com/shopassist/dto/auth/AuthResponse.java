package com.shopassist.dto.auth;

import java.time.Instant;

/**
 * Issued on successful signup or login.
 *
 * @param accessToken the bearer token to send as {@code Authorization: Bearer <token>}
 * @param tokenType   always "Bearer"
 * @param expiresAt   when the token stops working, so the client can refresh the
 *                    sign-in prompt rather than discovering it through a 401
 * @param user        the signed-in account
 */
public record AuthResponse(
        String accessToken,
        String tokenType,
        Instant expiresAt,
        UserProfileResponse user
) {
    public static AuthResponse of(String accessToken, Instant expiresAt, UserProfileResponse user) {
        return new AuthResponse(accessToken, "Bearer", expiresAt, user);
    }
}
