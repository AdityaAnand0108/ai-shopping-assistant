package com.shopassist.dto.auth;

import com.shopassist.entity.user.AppUser;

/**
 * The public view of an account.
 *
 * <p>Built field by field on purpose: the entity carries a password hash, a
 * primary key, and lockout counters, and none of them belong in a response. A
 * whitelist cannot leak a field that someone adds to the entity later.
 */
public record UserProfileResponse(
        String id,
        String username,
        String email,
        String fullName,
        String role
) {
    public static UserProfileResponse from(AppUser user) {
        return new UserProfileResponse(
                user.getPublicRef(),
                user.getUsername(),
                user.getEmail(),
                user.getFullName(),
                user.getRole().name());
    }
}
