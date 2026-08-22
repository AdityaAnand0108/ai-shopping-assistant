package com.shopassist.auth;

import com.shopassist.common.AccountLockedException;
import com.shopassist.common.AuthenticationFailedException;
import com.shopassist.common.DuplicateAccountException;
import com.shopassist.security.AppUserPrincipal;
import com.shopassist.security.CurrentUserService;
import com.shopassist.security.JwtService;
import com.shopassist.security.TokenDenylist;
import com.shopassist.user.AppUser;
import com.shopassist.user.AppUserRepository;
import com.shopassist.user.UserRole;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * Registration, sign-in and sign-out.
 */
@Service
@Slf4j
public class AuthService {

    /** Failed attempts tolerated before the account is locked. */
    static final int MAX_FAILED_ATTEMPTS = 5;

    /** How long a lockout lasts. */
    static final Duration LOCKOUT_DURATION = Duration.ofMinutes(15);

    /**
     * Hashed on a miss so that a request for an unknown username costs about the
     * same as one for a real account. Without this, response timing alone would
     * reveal which usernames exist, undoing the generic error message.
     */
    private static final String TIMING_DECOY_HASH =
            "$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy";

    private final AppUserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final TokenDenylist denylist;
    private final CurrentUserService currentUserService;

    public AuthService(AppUserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService,
                       TokenDenylist denylist,
                       CurrentUserService currentUserService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.denylist = denylist;
        this.currentUserService = currentUserService;
    }

    @Transactional
    public AuthResponse signup(SignupRequest request) {
        String username = request.username().trim();
        String email = request.email().trim().toLowerCase();

        if (userRepository.existsByUsernameIgnoreCase(username)) {
            throw new DuplicateAccountException("That username is already taken");
        }
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new DuplicateAccountException("That email address is already registered");
        }

        AppUser user = userRepository.save(AppUser.builder()
                .username(username)
                .email(email)
                .fullName(trimToNull(request.fullName()))
                .passwordHash(passwordEncoder.encode(request.password()))
                .role(UserRole.CUSTOMER)
                .enabled(true)
                .failedLoginAttempts(0)
                .build());

        log.info("Registered new account '{}'", username);
        return issueTokenFor(user);
    }

    /**
     * Signs a user in.
     *
     * <p>Not annotated {@code @Transactional} on purpose. A failed attempt has
     * to increment the counter and then throw; if the whole method shared one
     * transaction, that increment would roll back with the exception and the
     * lockout would never trigger. Letting each repository call manage its own
     * transaction means the counter is committed before the exception leaves.
     */
    public AuthResponse login(LoginRequest request) {
        Optional<AppUser> found = userRepository.findByUsername(request.username().trim());

        if (found.isEmpty()) {
            passwordEncoder.matches(request.password(), TIMING_DECOY_HASH);
            throw new AuthenticationFailedException();
        }

        AppUser user = found.get();

        if (user.isCurrentlyLocked()) {
            throw new AccountLockedException(user.getLockedUntil());
        }

        if (!user.isEnabled() || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            recordFailedAttempt(user);
            throw new AuthenticationFailedException();
        }

        if (user.getFailedLoginAttempts() > 0 || user.getLockedUntil() != null) {
            user.setFailedLoginAttempts(0);
            user.setLockedUntil(null);
            user = userRepository.save(user);
        }

        log.info("Sign-in succeeded for '{}'", user.getUsername());
        return issueTokenFor(user);
    }

    private void recordFailedAttempt(AppUser user) {
        int attempts = user.getFailedLoginAttempts() + 1;
        user.setFailedLoginAttempts(attempts);

        if (attempts >= MAX_FAILED_ATTEMPTS) {
            user.setLockedUntil(Instant.now().plus(LOCKOUT_DURATION));
            log.warn("Locked account '{}' after {} failed attempts", user.getUsername(), attempts);
        }
        userRepository.save(user);
    }

    /**
     * Revokes the caller's current token so it stops working immediately,
     * rather than trusting the client to discard it.
     */
    public void logout() {
        AppUserPrincipal principal = currentUserService.requirePrincipal();
        denylist.revoke(principal.tokenId(), principal.tokenExpiresAt());
        log.info("Revoked token for '{}' on sign-out", principal.username());
    }

    @Transactional(readOnly = true)
    public UserProfileResponse currentProfile() {
        return UserProfileResponse.from(currentUserService.requireUser());
    }

    private AuthResponse issueTokenFor(AppUser user) {
        JwtService.IssuedToken issued = jwtService.issue(user);
        return AuthResponse.of(issued.token(), issued.expiresAt(), UserProfileResponse.from(user));
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
