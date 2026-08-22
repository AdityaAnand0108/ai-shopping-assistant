package com.shopassist.exception.auth;

import java.time.Instant;

/**
 * Raised while a lockout from repeated failed sign-ins is still in effect.
 *
 * <p>This does reveal that the username exists, which is a deliberate trade:
 * a user locked out of their own account needs to be told why, and telling them
 * requires admitting the account is real. The exposure is bounded because it
 * only appears after several failed attempts, and those attempts are what
 * triggers the lockout in the first place. Recorded in the limitations note.
 */
public class AccountLockedException extends RuntimeException {

    private final Instant lockedUntil;

    public AccountLockedException(Instant lockedUntil) {
        super("Account temporarily locked");
        this.lockedUntil = lockedUntil;
    }

    public Instant getLockedUntil() {
        return lockedUntil;
    }
}
