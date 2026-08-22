package com.shopassist.security;

import com.shopassist.entity.user.AppUser;
import com.shopassist.enums.user.UserRole;
import java.time.Instant;

/**
 * The authenticated identity attached to a request.
 *
 * <p>This is the single source of truth for "who is asking". From Phase 5 the
 * assistant's tools read the user from here rather than from a parameter, which
 * is what makes it structurally impossible for the model to ask about somebody
 * else's account: there is no argument through which it could name one.
 *
 * <p>Carries {@link #userId} so owner-scoped queries need no extra lookup, but
 * that value is internal and must never be serialised into a response.
 */
public record AppUserPrincipal(
        Long userId,
        String publicRef,
        String username,
        UserRole role,
        String tokenId,
        Instant tokenExpiresAt
) {
    public static AppUserPrincipal of(AppUser user, String tokenId, Instant tokenExpiresAt) {
        return new AppUserPrincipal(user.getId(), user.getPublicRef(), user.getUsername(),
                user.getRole(), tokenId, tokenExpiresAt);
    }

    public String authority() {
        return "ROLE_" + role.name();
    }
}
