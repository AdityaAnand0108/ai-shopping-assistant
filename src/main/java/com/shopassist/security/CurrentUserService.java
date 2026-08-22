package com.shopassist.security;

import com.shopassist.user.AppUser;
import com.shopassist.user.AppUserRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Resolves the caller from the security context.
 *
 * <p>Everything that needs to know who is asking goes through here — REST
 * controllers now, and the assistant's tools from Phase 5. Centralising it means
 * there is exactly one place where identity enters the domain, and no code path
 * where identity arrives as untrusted input.
 */
@Service
public class CurrentUserService {

    private final AppUserRepository userRepository;

    public CurrentUserService(AppUserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public Optional<AppUserPrincipal> principal() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return Optional.empty();
        }
        return authentication.getPrincipal() instanceof AppUserPrincipal principal
                ? Optional.of(principal)
                : Optional.empty();
    }

    public AppUserPrincipal requirePrincipal() {
        return principal().orElseThrow(() ->
                new IllegalStateException("No authenticated user in the security context"));
    }

    /**
     * The caller's internal id, for owner-scoped queries. Reads straight from
     * the verified token, so this costs no database round trip.
     */
    public Long requireUserId() {
        return requirePrincipal().userId();
    }

    /** Loads the full user record. Prefer {@link #requireUserId()} when only the id is needed. */
    public AppUser requireUser() {
        Long userId = requireUserId();
        return userRepository.findById(userId).orElseThrow(() ->
                new IllegalStateException("Authenticated user no longer exists: " + userId));
    }
}
